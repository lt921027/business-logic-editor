package com.businesslogic.oplog;

import com.businesslogic.common.Result;
import com.businesslogic.dto.BusinessLogicSaveDTO;
import com.businesslogic.entity.LogicStep;
import com.businesslogic.groovy.service.FeatureSnapshot;
import com.businesslogic.groovy.service.GroovyBusinessLogicService;
import com.businesslogic.mapper.FeatureOperationLogMapper;
import com.businesslogic.vo.BusinessLogicVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 特征操作日志切面测试。
 *
 * <p>覆盖新增、修改（步骤变化 / 无变化 / 仅表达式变化）、单条删除、批量删除、
 * 批量删除部分成功、执行失败等分支。切面依赖的 Service 与 Mapper 全部用 Mock 替代，
 * 因此不依赖真实数据库。</p>
 */
public class FeatureOperationLogAspectTest {

    /** 带注解的样例方法，切面据此解析注解与入参 */
    static class SampleController {

        @OpLog(featureIdProperty = "id")
        public Result<BusinessLogicVO> save(BusinessLogicSaveDTO dto) {
            return null;
        }

        @OpLog(operation = OpLogConsts.OP_DELETE, featureIdArg = 0)
        public Result<Void> deleteBatch(List<Long> ids) {
            return null;
        }
    }

    private final GroovyBusinessLogicService businessLogicService = mock(GroovyBusinessLogicService.class);

    private final FeatureOperationLogMapper logMapper = mock(FeatureOperationLogMapper.class);

    private final FeatureOperationLogService logService = new FeatureOperationLogService(logMapper);

    private final FeatureOperationLogAspect aspect = new FeatureOperationLogAspect(
            businessLogicService, logService, objectMapper());

    private static ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    // ==================== 夹具 ====================

    private ProceedingJoinPoint joinPoint(String methodName, Class<?>[] types, Object[] args,
                                          Object result, Throwable error) throws Throwable {
        Method method = SampleController.class.getMethod(methodName, types);

        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        when(signature.toShortString()).thenReturn("SampleController." + methodName);

        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getTarget()).thenReturn(new SampleController());
        when(joinPoint.getArgs()).thenReturn(args);
        if (error != null) {
            when(joinPoint.proceed()).thenThrow(error);
        } else {
            when(joinPoint.proceed()).thenReturn(result);
        }
        return joinPoint;
    }

    private ProceedingJoinPoint saveJoinPoint(BusinessLogicSaveDTO dto, Object result, Throwable error)
            throws Throwable {
        return joinPoint("save", new Class<?>[]{BusinessLogicSaveDTO.class}, new Object[]{dto}, result, error);
    }

    private ProceedingJoinPoint deleteBatchJoinPoint(List<Long> ids, Object result, Throwable error)
            throws Throwable {
        return joinPoint("deleteBatch", new Class<?>[]{List.class}, new Object[]{ids}, result, error);
    }

    /** 构造一份快照：表达式 + 指定输出变量的步骤 */
    private FeatureSnapshot snapshot(String expression, String... outputVars) {
        List<LogicStep> steps = new ArrayList<>();
        int order = 1;
        for (String outputVar : outputVars) {
            LogicStep step = new LogicStep();
            step.setId(100L + order);
            step.setBusinessLogicId(5L);
            step.setStepOrder(order);
            step.setFunctionCategory("direct");
            step.setOutputVar(outputVar);
            steps.add(step);
            order++;
        }
        return new FeatureSnapshot(expression, steps);
    }

    private BusinessLogicSaveDTO dto(Long id) {
        BusinessLogicSaveDTO dto = new BusinessLogicSaveDTO();
        dto.setId(id);
        dto.setName("订单金额校验");
        return dto;
    }

    private Result<BusinessLogicVO> resultWithId(Long id) {
        BusinessLogicVO vo = new BusinessLogicVO();
        vo.setId(id);
        return Result.success(vo);
    }

    private FeatureOperationLog captureSaved() {
        ArgumentCaptor<FeatureOperationLog> captor = ArgumentCaptor.forClass(FeatureOperationLog.class);
        verify(logMapper).insert(captor.capture());
        return captor.getValue();
    }

    private List<FeatureOperationLog> captureSavedList(int expectedCount) {
        ArgumentCaptor<FeatureOperationLog> captor = ArgumentCaptor.forClass(FeatureOperationLog.class);
        verify(logMapper, times(expectedCount)).insert(captor.capture());
        return captor.getAllValues();
    }

    // ==================== 用例 ====================

    /** 新增：入参没有ID，记为 INSERT；ID 从返回值里取；没有"改前" */
    @Test
    public void shouldRecordInsertAndTakeFeatureIdFromResult() throws Throwable {
        when(businessLogicService.getSnapshot(66L)).thenReturn(snapshot("return 1", "step1"));

        aspect.around(saveJoinPoint(dto(null), resultWithId(66L), null));

        FeatureOperationLog log = captureSaved();
        assertEquals(OpLogConsts.OP_INSERT, log.getOperation());
        assertEquals(Long.valueOf(66L), log.getFeatureId());
        assertEquals(Integer.valueOf(1), log.getSuccess());
        assertNull(log.getExpressionBefore());
        assertNull(log.getStepDataBefore());
        assertNull(log.getChangedFields());
        assertEquals("return 1", log.getExpressionAfter());
        assertNotNull(log.getExpressionHashAfter());
        assertNotNull(log.getStepDataAfter());
        assertNotNull(log.getRequestSnapshot());
        assertNotNull(log.getCreatedAt());
    }

    /** 修改：入参带ID，记为 UPDATE；前后快照都查库；步骤不同记"步骤" */
    @Test
    public void shouldRecordUpdateWithBeforeAndAfterSnapshots() throws Throwable {
        when(businessLogicService.getSnapshot(5L))
                .thenReturn(snapshot("return 1", "step1"))
                .thenReturn(snapshot("return 2", "step2"));

        aspect.around(saveJoinPoint(dto(5L), resultWithId(5L), null));

        FeatureOperationLog log = captureSaved();
        assertEquals(OpLogConsts.OP_UPDATE, log.getOperation());
        assertEquals(Long.valueOf(5L), log.getFeatureId());
        assertEquals("return 1", log.getExpressionBefore());
        assertEquals("return 2", log.getExpressionAfter());
        assertNotNull(log.getStepDataBefore());
        assertNotNull(log.getStepDataAfter());
        assertEquals(OpLogConsts.CHANGE_STEPS, log.getChangedFields());
    }

    /** 内容没变：步骤与表达式都相同，记"无变化" */
    @Test
    public void shouldMarkNoChangeWhenNothingChanged() throws Throwable {
        FeatureSnapshot same = snapshot("return 1", "step1");
        when(businessLogicService.getSnapshot(5L)).thenReturn(same, same);

        aspect.around(saveJoinPoint(dto(5L), resultWithId(5L), null));

        assertEquals(OpLogConsts.CHANGE_NONE, captureSaved().getChangedFields());
    }

    /** 步骤没变但表达式变了（改名称/默认值/返回类型）：记"表达式" */
    @Test
    public void shouldMarkExpressionWhenOnlyExpressionChanged() throws Throwable {
        when(businessLogicService.getSnapshot(5L))
                .thenReturn(snapshot("return 1", "step1"))
                .thenReturn(snapshot("return 999", "step1"));

        aspect.around(saveJoinPoint(dto(5L), resultWithId(5L), null));

        assertEquals(OpLogConsts.CHANGE_EXPRESSION, captureSaved().getChangedFields());
    }

    /** 执行失败：仍落库，标记失败并保留"改前"快照，"改后"留空 */
    @Test
    public void shouldRecordFailureAndKeepBeforeSnapshot() throws Throwable {
        when(businessLogicService.getSnapshot(5L)).thenReturn(snapshot("return 1", "step1"));

        IllegalStateException error = new IllegalStateException("默认值不能为空");
        assertThrows(IllegalStateException.class,
                () -> aspect.around(saveJoinPoint(dto(5L), null, error)));

        FeatureOperationLog log = captureSaved();
        assertEquals(OpLogConsts.OP_UPDATE, log.getOperation());
        assertEquals(Long.valueOf(5L), log.getFeatureId());
        assertEquals(Integer.valueOf(0), log.getSuccess());
        assertEquals("默认值不能为空", log.getErrorMsg());
        assertEquals("return 1", log.getExpressionBefore());
        assertNull(log.getExpressionAfter());
        assertNull(log.getStepDataAfter());
        assertNull(log.getChangedFields());
    }

    /** 单条删除：记 DELETE，只查"改前"，没有"改后" */
    @Test
    public void shouldRecordSingleDeleteWithoutAfterSnapshot() throws Throwable {
        when(businessLogicService.getSnapshot(9L)).thenReturn(snapshot("return 1", "step1"));

        aspect.around(deleteBatchJoinPoint(Arrays.asList(9L), Result.success("删除成功", null), null));

        FeatureOperationLog log = captureSaved();
        assertEquals(OpLogConsts.OP_DELETE, log.getOperation());
        assertEquals(Long.valueOf(9L), log.getFeatureId());
        assertEquals(Integer.valueOf(1), log.getSuccess());
        assertEquals("return 1", log.getExpressionBefore());
        assertNull(log.getExpressionAfter());
        assertNull(log.getStepDataAfter());
        assertNull(log.getChangedFields());
    }

    /** 批量删除：每个特征一行，各自带自己的"改前"快照 */
    @Test
    public void shouldRecordOneRowPerFeatureOnBatchDelete() throws Throwable {
        when(businessLogicService.getSnapshot(11L)).thenReturn(snapshot("return 11", "step1"));
        when(businessLogicService.getSnapshot(12L)).thenReturn(snapshot("return 12", "step1"));

        aspect.around(deleteBatchJoinPoint(Arrays.asList(11L, 12L), Result.success("删除成功", null), null));

        List<FeatureOperationLog> rows = captureSavedList(2);
        assertEquals(Long.valueOf(11L), rows.get(0).getFeatureId());
        assertEquals(Long.valueOf(12L), rows.get(1).getFeatureId());
        assertEquals("return 11", rows.get(0).getExpressionBefore());
        assertEquals("return 12", rows.get(1).getExpressionBefore());
        for (FeatureOperationLog row : rows) {
            assertEquals(OpLogConsts.OP_DELETE, row.getOperation());
            assertEquals(Integer.valueOf(1), row.getSuccess());
            assertNull(row.getExpressionAfter());
        }
    }

    /** 批量删除中途失败：已删掉的记成功，未删掉的记失败并带异常原因 */
    @Test
    public void shouldMarkPartialSuccessWhenBatchDeleteFailsHalfway() throws Throwable {
        // 11 号在异常之前已经删掉：改前能查到，事后查为 null
        when(businessLogicService.getSnapshot(11L))
                .thenReturn(snapshot("return 11", "step1"))
                .thenReturn(null);
        // 12 号没被处理：改前事后都能查到
        when(businessLogicService.getSnapshot(12L))
                .thenReturn(snapshot("return 12", "step1"))
                .thenReturn(snapshot("return 12", "step1"));

        RuntimeException error = new RuntimeException("第 2 条删除失败");
        assertThrows(RuntimeException.class,
                () -> aspect.around(deleteBatchJoinPoint(Arrays.asList(11L, 12L), null, error)));

        List<FeatureOperationLog> rows = captureSavedList(2);
        assertEquals(Integer.valueOf(1), rows.get(0).getSuccess());
        assertNull(rows.get(0).getErrorMsg());
        assertEquals(Integer.valueOf(0), rows.get(1).getSuccess());
        assertEquals("第 2 条删除失败", rows.get(1).getErrorMsg());
        assertEquals("return 12", rows.get(1).getExpressionBefore());
    }
}
