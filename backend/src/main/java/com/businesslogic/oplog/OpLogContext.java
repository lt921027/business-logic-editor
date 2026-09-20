package com.businesslogic.oplog;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 操作日志上下文：开发人员在业务方法内部登记待记录的操作日志，切面在方法结束时取出并落库。
 *
 * <p><b>为什么需要它：</b>切面在方法外面，只能拿到入参和返回值；而"对象ID、描述文字、操作人"
 * 这类信息只有方法内部才知道。上下文就是两者之间的传递通道，内部结构是栈，因此支持嵌套调用——
 * 外层方法记外层的一条，内层方法记内层的一条，互不覆盖。</p>
 *
 * <p><b>使用要点：</b></p>
 * <ol>
 *   <li>在方法开头登记（尤其是删除、修改这类操作，先登记再去动数据）；</li>
 *   <li>登记后即使方法抛异常，切面也会照常落库，并把 success 置 0、写入 error_msg；</li>
 *   <li>方法内部自己 catch 掉异常并返回失败结果时，必须调用 {@link #fail(String)} 标记失败，
 *       否则切面只能看到"方法正常返回"，会把这次操作记成成功；</li>
 *   <li>登记只影响当前线程，业务结束后由切面统一清理，业务代码不需要手动清理。</li>
 * </ol>
 */
public final class OpLogContext {

    /**
     * 当前线程待记录的操作日志栈。
     *
     * <p>用栈而不是单个对象，是为了让嵌套的注解方法各自记录一条，互不干扰。</p>
     */
    private static final ThreadLocal<Deque<OperationLog>> HOLDER =
            ThreadLocal.withInitial(ArrayDeque::new);

    private OpLogContext() {
    }

    /**
     * 登记一条操作日志（最常用，操作人也由这里填写）。
     *
     * <pre>
     * // 新增源报文
     * OpLogContext.set(OpLogConsts.BIZ_SOURCE, OpLogConsts.OP_INSERT, sourceNo,
     *         "新增源报文 " + sourceNo, operator);
     *
     * // 删除特征配置（描述里带名称，保证删除后仍看得懂）
     * OpLogContext.set(OpLogConsts.BIZ_FEATURE, OpLogConsts.OP_DELETE, String.valueOf(id),
     *         "删除特征 " + id + "（" + name + "）", operator);
     * </pre>
     *
     * @param bizType   对象类型，取值见 {@link OpLogConsts}
     * @param operation 操作类型，取值见 {@link OpLogConsts}
     * @param bizId     对象ID：源报文编号 或 特征配置ID
     * @param desc      描述文字，展示端直接显示
     * @param operator  操作人，由业务侧填写，取不到时传 null
     */
    public static void set(String bizType, String operation, String bizId, String desc, String operator) {
        OperationLog log = new OperationLog();
        log.setBizType(bizType);
        log.setOperation(operation);
        log.setBizId(bizId);
        log.setOperationDesc(desc);
        log.setOperator(operator);
        HOLDER.get().push(log);
    }

    /**
     * 登记一条操作日志（不填操作人）。
     */
    public static void set(String bizType, String operation, String bizId, String desc) {
        set(bizType, operation, bizId, desc, null);
    }

    /**
     * 登记一条操作日志（需要自行设置更多字段时使用）。
     *
     * <p>只需设置 bizType / bizId / operation / operationDesc / operator，其余字段由切面补齐。</p>
     */
    public static void set(OperationLog log) {
        if (log != null) {
            HOLDER.get().push(log);
        }
    }

    /**
     * 在当前这条日志的末尾追加文字，用于"操作执行完之后才知道的信息"，例如步骤数变化。
     *
     * <pre>
     * OpLogContext.set(OpLogConsts.BIZ_FEATURE, OpLogConsts.OP_UPDATE, String.valueOf(id),
     *         "修改特征 " + id, operator);
     * BusinessLogicVO vo = service.update(id, dto);
     * OpLogContext.appendDesc("，步骤 " + beforeCount + "→" + vo.getStepCount());
     * </pre>
     *
     * @param suffix 追加内容，为 null 时忽略
     */
    public static void appendDesc(String suffix) {
        if (suffix == null) {
            return;
        }
        OperationLog log = peek();
        if (log != null) {
            String old = log.getOperationDesc() == null ? "" : log.getOperationDesc();
            log.setOperationDesc(old + suffix);
        }
    }

    /**
     * 把当前这条日志标记为失败。
     *
     * <p>业务代码自己 catch 异常并返回失败结果（而不是继续往外抛）时，必须调用本方法。</p>
     *
     * @param errorMsg 失败原因
     */
    public static void fail(String errorMsg) {
        OperationLog log = peek();
        if (log != null) {
            log.setSuccess(0);
            log.setErrorMsg(errorMsg);
        }
    }

    /**
     * 补充当前这条日志的操作人（登记之后才拿到操作人时使用）。
     */
    public static void setOperator(String operator) {
        OperationLog log = peek();
        if (log != null) {
            log.setOperator(operator);
        }
    }

    /**
     * 查看当前待记录的日志，不存在返回 null。
     */
    public static OperationLog peek() {
        return HOLDER.get().peek();
    }

    /**
     * 取出栈顶日志并清理线程变量，仅供切面调用。
     */
    static OperationLog pop() {
        Deque<OperationLog> stack = HOLDER.get();
        OperationLog log = stack.poll();
        if (stack.isEmpty()) {
            HOLDER.remove();
        }
        return log;
    }
}
