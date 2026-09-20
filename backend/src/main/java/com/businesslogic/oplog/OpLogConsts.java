package com.businesslogic.oplog;

/**
 * 操作日志通用常量：对象类型与操作类型。
 *
 * <p>开发人员登记操作日志时直接引用这里的常量，避免手写字符串出现拼写不一致。</p>
 *
 * <pre>
 * OpLogContext.set(OpLogConsts.BIZ_SOURCE, OpLogConsts.OP_INSERT, sourceNo,
 *         "新增源报文 " + sourceNo, operator);
 * </pre>
 */
public final class OpLogConsts {

    private OpLogConsts() {
    }

    /** 对象类型：源报文 */
    public static final String BIZ_SOURCE = "SOURCE";

    /** 对象类型：特征配置 */
    public static final String BIZ_FEATURE = "FEATURE";

    /** 操作类型：新增 */
    public static final String OP_INSERT = "INSERT";

    /** 操作类型：修改 */
    public static final String OP_UPDATE = "UPDATE";

    /** 操作类型：删除 */
    public static final String OP_DELETE = "DELETE";
}
