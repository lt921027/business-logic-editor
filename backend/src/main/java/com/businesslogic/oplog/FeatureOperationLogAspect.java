package com.businesslogic.oplog;

import com.businesslogic.groovy.service.FeatureSnapshot;
import com.businesslogic.groovy.service.GroovyBusinessLogicService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 特征操作日志切面：拦截所有标注 {@link OpLog} 的 Controller 方法，记录特征的增删改。
 *
 * <p>埋点只需在 Controller 方法上贴一个 {@link OpLog} 注解，业务代码零侵入：
 * 特征ID、操作类型从入参与注解解析，"修改前"在方法执行之前查库获得，
 * "修改后"在方法成功返回且事务提交之后查库获得。</p>
 *
 * <p><b>为什么 {@code @Order(Ordered.HIGHEST_PRECEDENCE)}：</b>业务 Service 上带
 * {@code @Transactional}，切面排到最外层后，finally 会在事务提交或回滚之后执行，
 * 此时查到的才是真正落库的值；再叠加 {@link FeatureOperationLogService#save} 的独立事务，
 * 业务回滚时日志依然保留。</p>
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FeatureOperationLogAspect {

    private static final Logger logger = LoggerFactory.getLogger(FeatureOperationLogAspect.class);

    /** 失败原因最大长度，与表结构对齐 */
    private static final int ERROR_MAX_LENGTH = 500;

    /** 操作人最大长度，与表结构对齐 */
    private static final int OPERATOR_MAX_LENGTH = 64;

    /**
     * 比对步骤时忽略的字段。
     *
     * <p>步骤是"先删后插"的，落库后 id 与时间戳每次都会变，不剔除会导致每次都判定为"步骤变化"。</p>
     */
    private static final Set<String> VOLATILE_STEP_FIELDS = new HashSet<>(Arrays.asList(
            "id", "businessLogicId", "createdAt", "updatedAt", "deleted"));

    private final GroovyBusinessLogicService businessLogicService;
    private final FeatureOperationLogService featureOperationLogService;
    private final ObjectMapper objectMapper;

    /** 开关，方便在本地或压测环境整体关闭 */
    @Value("${operation-log.enabled:true}")
    private boolean enabled = true;

    public FeatureOperationLogAspect(GroovyBusinessLogicService businessLogicService,
                                     FeatureOperationLogService featureOperationLogService,
                                     ObjectMapper objectMapper) {
        this.businessLogicService = businessLogicService;
        this.featureOperationLogService = featureOperationLogService;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(com.businesslogic.oplog.OpLog)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!enabled) {
            return joinPoint.proceed();
        }

        OpLog annotation = findAnnotation(joinPoint);
        if (annotation == null) {
            return joinPoint.proceed();
        }

        List<Long> featureIds = resolveFeatureIds(joinPoint, annotation);
        String operation = resolveOperation(annotation, featureIds);
        List<FeatureOperationLog> operationLogs =
                prepareLogs(operation, featureIds, toJson(joinPoint.getArgs()));

        Throwable error = null;
        Object result = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            try {
                finish(operationLogs, joinPoint, result, error);
                for (FeatureOperationLog operationLog : operationLogs) {
                    featureOperationLogService.save(operationLog);
                }
            } catch (Exception e) {
                // 记录日志失败绝不能影响业务
                logger.warn("[特征操作日志] 记录失败: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * 按操作类型准备待落库的日志行。
     *
     * <p>新增只可能有一行且ID要等落库后才知道；修改与删除按特征ID 一行一条，
     * 每行的"改前"快照都在动手之前查好。</p>
     */
    private List<FeatureOperationLog> prepareLogs(String operation, List<Long> featureIds, String requestSnapshot) {
        List<FeatureOperationLog> operationLogs = new ArrayList<>();
        if (OpLogConsts.OP_INSERT.equals(operation)) {
            FeatureOperationLog operationLog = new FeatureOperationLog();
            operationLog.setOperation(operation);
            operationLog.setRequestSnapshot(requestSnapshot);
            operationLogs.add(operationLog);
            return operationLogs;
        }

        for (Long featureId : featureIds) {
            FeatureOperationLog operationLog = new FeatureOperationLog();
            operationLog.setOperation(operation);
            operationLog.setFeatureId(featureId);
            operationLog.setRequestSnapshot(requestSnapshot);
            fillSnapshot(operationLog, featureId, true);
            operationLogs.add(operationLog);
        }
        return operationLogs;
    }

    /**
     * 逐行补齐执行结果相关的字段。
     *
     * <p>批量场景下可能出现"前面的条目已成功、后面的条目失败"，因此失败时要逐条判定，
     * 不能把整个请求的结果套到每一行上。</p>
     */
    private void finish(List<FeatureOperationLog> operationLogs, JoinPoint joinPoint,
                        Object result, Throwable error) {
        for (FeatureOperationLog operationLog : operationLogs) {
            operationLog.setCreatedAt(LocalDateTime.now());
            operationLog.setOperator(truncate(resolveOperator(joinPoint), OPERATOR_MAX_LENGTH));

            if (OpLogConsts.OP_INSERT.equals(operationLog.getOperation())) {
                finishInsert(operationLog, result, error);
                continue;
            }

            if (error == null) {
                operationLog.setSuccess(1);
                if (OpLogConsts.OP_UPDATE.equals(operationLog.getOperation())) {
                    fillSnapshot(operationLog, operationLog.getFeatureId(), false);
                    operationLog.setChangedFields(resolveChangedFields(operationLog));
                }
                // 删除没有"改后"：记录已被逻辑删除，查也查不到
                continue;
            }

            finishFailed(operationLog, error);
        }
    }

    /**
     * 新增的结果：失败只记原因；成功从返回值取ID 并补"改后"快照。
     */
    private void finishInsert(FeatureOperationLog operationLog, Object result, Throwable error) {
        if (error != null) {
            operationLog.setSuccess(0);
            operationLog.setErrorMsg(errorMessage(error));
            return;
        }

        operationLog.setSuccess(1);
        operationLog.setFeatureId(resolveIdFromResult(result));
        if (operationLog.getFeatureId() == null) {
            return;
        }
        fillSnapshot(operationLog, operationLog.getFeatureId(), false);
    }

    /**
     * 执行失败时的逐条判定。
     *
     * <p>删除操作如果库里已经查不到这条特征，说明它在异常发生之前已经删掉了，
     * 记为成功；其余情况记为失败并带上异常原因。</p>
     */
    private void finishFailed(FeatureOperationLog operationLog, Throwable error) {
        if (OpLogConsts.OP_DELETE.equals(operationLog.getOperation()) && isGone(operationLog.getFeatureId())) {
            operationLog.setSuccess(1);
            return;
        }
        operationLog.setSuccess(0);
        operationLog.setErrorMsg(errorMessage(error));
    }

    /**
     * 判断特征是否已经不在库里（逻辑删除后查询会返回不存在）。
     */
    private boolean isGone(Long featureId) {
        if (featureId == null) {
            return false;
        }
        try {
            return businessLogicService.getSnapshot(featureId) == null;
        } catch (Exception e) {
            // 查询本身失败时无法判断是否已删除，按"未删除"处理，宁可记成失败
            logger.debug("[特征操作日志] 判断特征是否存在失败, featureId={}: {}", featureId, e.getMessage());
            return false;
        }
    }

    private String errorMessage(Throwable error) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        return truncate(message, ERROR_MAX_LENGTH);
    }

    /**
     * 查库填充一份快照：表达式、表达式 MD5、步骤（实体驼峰）与步骤（VO 结构）。
     *
     * <p>记录不存在或查询失败时整体留空，绝不打断业务。</p>
     */
    private void fillSnapshot(FeatureOperationLog operationLog, Long featureId, boolean before) {
        String expression = null;
        String stepJson = null;
        try {
            FeatureSnapshot snapshot = businessLogicService.getSnapshot(featureId);
            if (snapshot != null) {
                expression = snapshot.getGroovyExpression();
                stepJson = toJson(snapshot.getSteps());
            }
        } catch (Exception e) {
            logger.debug("[特征操作日志] 查询特征快照失败, featureId={}, before={}: {}",
                    featureId, before, e.getMessage());
        }
        String expressionHash = md5(expression);

        if (before) {
            operationLog.setExpressionBefore(expression);
            operationLog.setExpressionHashBefore(expressionHash);
            operationLog.setStepDataBefore(stepJson);
        } else {
            operationLog.setExpressionAfter(expression);
            operationLog.setExpressionHashAfter(expressionHash);
            operationLog.setStepDataAfter(stepJson);
        }
    }

    /**
     * 计算变更项：步骤变 → "步骤"；步骤没变但表达式变了 → "表达式"；都没变 → "无变化"。
     *
     * <p>表达式由步骤、特征名称、默认值、返回类型一起生成，所以"步骤未变而表达式变化"
     * 对应的是名称/默认值/返回类型的调整。</p>
     */
    private String resolveChangedFields(FeatureOperationLog operationLog) {
        List<Map<String, Object>> beforeSteps = normalizeSteps(operationLog.getStepDataBefore());
        List<Map<String, Object>> afterSteps = normalizeSteps(operationLog.getStepDataAfter());
        if (!beforeSteps.equals(afterSteps)) {
            return OpLogConsts.CHANGE_STEPS;
        }

        String beforeHash = operationLog.getExpressionHashBefore();
        String afterHash = operationLog.getExpressionHashAfter();
        if (beforeHash == null && afterHash == null) {
            return OpLogConsts.CHANGE_NONE;
        }
        return beforeHash != null && beforeHash.equals(afterHash)
                ? OpLogConsts.CHANGE_NONE
                : OpLogConsts.CHANGE_EXPRESSION;
    }

    /**
     * 步骤比对前做归一化：剔除"先删后插"导致的易变字段，并按步骤顺序排序。
     */
    private List<Map<String, Object>> normalizeSteps(String stepJson) {
        if (isBlank(stepJson)) {
            return new ArrayList<>();
        }
        try {
            List<Map<String, Object>> steps =
                    objectMapper.readValue(stepJson, new TypeReference<List<Map<String, Object>>>() {
                    });
            List<Map<String, Object>> normalized = new ArrayList<>(steps.size());
            for (Map<String, Object> step : steps) {
                Map<String, Object> copy = new LinkedHashMap<>(step);
                VOLATILE_STEP_FIELDS.forEach(copy::remove);
                normalized.add(copy);
            }
            normalized.sort(Comparator.comparingInt(step -> {
                Object order = step.get("stepOrder");
                return order instanceof Number ? ((Number) order).intValue() : 0;
            }));
            return normalized;
        } catch (Exception e) {
            logger.debug("[特征操作日志] 步骤归一化失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 解析特征ID 列表。
     *
     * <p>先按注解声明的参数下标取；该参数是对象时按属性名取；都不行再逐个参数查找属性。
     * 参数或属性值既可以是单个ID，也可以是ID集合（批量接口）。返回空列表表示本次是新增。</p>
     */
    private List<Long> resolveFeatureIds(ProceedingJoinPoint joinPoint, OpLog annotation) {
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return new ArrayList<>();
        }

        int index = annotation.featureIdArg();
        if (index >= 0 && index < args.length) {
            List<Long> featureIds = toFeatureIds(args[index]);
            if (!featureIds.isEmpty()) {
                return featureIds;
            }
            featureIds = toFeatureIds(readProperty(args[index], annotation.featureIdProperty()));
            if (!featureIds.isEmpty()) {
                return featureIds;
            }
        }

        if (!isBlank(annotation.featureIdProperty())) {
            for (Object arg : args) {
                List<Long> featureIds = toFeatureIds(readProperty(arg, annotation.featureIdProperty()));
                if (!featureIds.isEmpty()) {
                    return featureIds;
                }
            }
        }
        return new ArrayList<>();
    }

    /**
     * 解析操作类型：注解显式声明优先，否则按是否带特征ID 判定为修改或新增。
     */
    private String resolveOperation(OpLog annotation, List<Long> featureIds) {
        if (!isBlank(annotation.operation())) {
            return annotation.operation().trim();
        }
        return featureIds.isEmpty() ? OpLogConsts.OP_INSERT : OpLogConsts.OP_UPDATE;
    }

    /**
     * 把参数值转成特征ID 列表：支持单个ID 与ID集合，顺带过滤 null、0 与非法值。
     */
    private List<Long> toFeatureIds(Object value) {
        List<Long> featureIds = new ArrayList<>();
        if (value == null) {
            return featureIds;
        }
        if (value instanceof Collection) {
            for (Object item : (Collection<?>) value) {
                Long featureId = toFeatureId(item);
                if (featureId != null) {
                    featureIds.add(featureId);
                }
            }
            return featureIds;
        }
        Long featureId = toFeatureId(value);
        if (featureId != null) {
            featureIds.add(featureId);
        }
        return featureIds;
    }

    /**
     * 从返回值里取特征ID，兼容直接返回 VO 与统一响应包装 {@code Result{data:VO}} 两种形态。
     */
    private Long resolveIdFromResult(Object result) {
        if (result == null) {
            return null;
        }
        Long id = toFeatureId(readProperty(result, "id"));
        if (id != null) {
            return id;
        }
        return toFeatureId(readProperty(readProperty(result, "data"), "id"));
    }

    /**
     * 把参数值转成特征ID：null、0、空串、非数字都视为"没有ID"。
     */
    private Long toFeatureId(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            long number = ((Number) value).longValue();
            return number > 0 ? number : null;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            long number = Long.parseLong(text);
            return number > 0 ? number : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 按属性名读取 getter 返回值，读不到返回 null。
     */
    private Object readProperty(Object target, String property) {
        if (target == null || isBlank(property)) {
            return null;
        }
        String name = property.trim();
        String getter = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        try {
            Method method = target.getClass().getMethod(getter);
            return method.invoke(target);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 操作人取值。
     *
     * <p>当前返回 null，字段预留给业务自行补充取值逻辑（session、token、请求头均可），
     * 只需要改写这一个方法。</p>
     */
    protected String resolveOperator(JoinPoint joinPoint) {
        return null;
    }

    private OpLog findAnnotation(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = AopUtils.getMostSpecificMethod(signature.getMethod(), joinPoint.getTarget().getClass());
        return method.getAnnotation(OpLog.class);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            logger.debug("[特征操作日志] JSON 序列化失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 计算文本的 MD5（32位小写），入参为 null 时返回 null。
     */
    private String md5(String text) {
        if (text == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(Character.forDigit((b >> 4) & 0xF, 16));
                builder.append(Character.forDigit(b & 0xF, 16));
            }
            return builder.toString();
        } catch (Exception e) {
            logger.debug("[特征操作日志] 计算表达式 MD5 失败: {}", e.getMessage());
            return null;
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
