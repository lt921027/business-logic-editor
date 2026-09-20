package com.businesslogic.oplog;

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
import java.time.LocalDateTime;

/**
 * 操作日志切面：拦截所有标注 {@link OpLog} 的方法，把开发人员登记的日志补齐并落库。
 *
 * <p>它只做四件事，不包含任何业务判断：</p>
 * <ol>
 *   <li>决定"记不记"——只有标了 {@link OpLog} 的方法才会被拦截；</li>
 *   <li>补齐字段——success、errorMsg、createdAt；</li>
 *   <li>成功失败都落库——方法抛异常时同样写一条，并标记 success=0；</li>
 *   <li>兜底与防漏——抛异常但开发人员忘记登记时按注解声明记一条，
 *       写库失败只打日志，绝不影响业务。</li>
 * </ol>
 *
 * <p><b>为什么 {@code @Order(Ordered.HIGHEST_PRECEDENCE)}：</b>业务方法上通常有
 * {@code @Transactional}，切面排到最外层后，finally 会在事务提交或回滚之后执行；
 * 再叠加 {@link OperationLogService#save} 的独立事务，才能保证业务回滚时日志依然留下。</p>
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OperationLogAspect {

    private static final Logger logger = LoggerFactory.getLogger(OperationLogAspect.class);

    /** 描述文字最大长度，与表结构 varchar(500) 对齐 */
    private static final int DESC_MAX_LENGTH = 500;

    /** 失败原因最大长度，与表结构 varchar(500) 对齐 */
    private static final int ERROR_MAX_LENGTH = 500;

    /** 操作人最大长度，与表结构 varchar(64) 对齐 */
    private static final int OPERATOR_MAX_LENGTH = 64;

    private final OperationLogService operationLogService;

    /** 开关，方便在本地或压测环境整体关闭 */
    @Value("${operation-log.enabled:true}")
    private boolean enabled = true;

    public OperationLogAspect(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @Around("@annotation(com.businesslogic.oplog.OpLog)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Throwable error = null;
        try {
            return joinPoint.proceed();
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            // 无论是否开启记录，都要清理线程变量，避免线程复用导致脏数据
            OperationLog operationLog = OpLogContext.pop();
            if (enabled) {
                try {
                    record(joinPoint, operationLog, error);
                } catch (Exception e) {
                    // 记录日志失败绝不能影响业务
                    logger.warn("[操作日志] 落库失败: {}", e.getMessage(), e);
                }
            }
        }
    }

    /**
     * 补齐字段并落库。
     */
    private void record(ProceedingJoinPoint joinPoint, OperationLog operationLog, Throwable error) {
        OperationLog target = operationLog;
        if (target == null) {
            // 未登记：只有抛异常时才兜底记录，正常返回的漏登记只提醒不落库
            if (error == null) {
                logger.warn("[操作日志] 方法 {} 未登记操作日志（未调用 OpLogContext.set）",
                        joinPoint.getSignature().toShortString());
                return;
            }
            target = fallback(joinPoint);
            if (target == null) {
                logger.warn("[操作日志] 方法 {} 未登记操作日志，且注解未声明兜底类型，异常未记录: {}",
                        joinPoint.getSignature().toShortString(), error.getMessage());
                return;
            }
        }

        fill(target, error);
        operationLogService.save(target);
    }

    /**
     * 补齐开发人员不需要关心的字段。
     */
    private void fill(OperationLog operationLog, Throwable error) {
        // 业务代码显式标记过成功/失败就以业务为准（例如自行 catch 后调用了 OpLogContext.fail）
        if (operationLog.getSuccess() == null) {
            operationLog.setSuccess(error == null ? 1 : 0);
        }
        if (operationLog.getErrorMsg() == null && error != null) {
            String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            operationLog.setErrorMsg(truncate(message, ERROR_MAX_LENGTH));
        }
        if (operationLog.getCreatedAt() == null) {
            operationLog.setCreatedAt(LocalDateTime.now());
        }
        operationLog.setOperationDesc(truncate(operationLog.getOperationDesc(), DESC_MAX_LENGTH));
        operationLog.setErrorMsg(truncate(operationLog.getErrorMsg(), ERROR_MAX_LENGTH));
        operationLog.setOperator(truncate(operationLog.getOperator(), OPERATOR_MAX_LENGTH));
    }

    /**
     * 开发人员忘记登记又抛了异常时，用注解上的声明生成一条兜底日志，保证异常不被静默丢掉。
     *
     * @return 兜底日志；注解未声明 bizType 时返回 null
     */
    private OperationLog fallback(ProceedingJoinPoint joinPoint) {
        OpLog annotation = findAnnotation(joinPoint);
        if (annotation == null || isBlank(annotation.bizType())) {
            return null;
        }
        OperationLog operationLog = new OperationLog();
        operationLog.setBizType(annotation.bizType());
        operationLog.setOperation(annotation.operation());
        return operationLog;
    }

    private OpLog findAnnotation(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = AopUtils.getMostSpecificMethod(signature.getMethod(), joinPoint.getTarget().getClass());
        return method.getAnnotation(OpLog.class);
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
