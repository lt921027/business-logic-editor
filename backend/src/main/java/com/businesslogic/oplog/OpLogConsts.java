package com.businesslogic.oplog;

/**
 * 特征操作日志常量。
 */
public final class OpLogConsts {

    private OpLogConsts() {
    }

    /** 操作类型：新增 */
    public static final String OP_INSERT = "INSERT";

    /** 操作类型：修改 */
    public static final String OP_UPDATE = "UPDATE";

    /** 操作类型：删除 */
    public static final String OP_DELETE = "DELETE";

    /** 变更项：步骤发生变化 */
    public static final String CHANGE_STEPS = "步骤";

    /** 变更项：步骤未变但表达式变化（名称、默认值、返回类型等） */
    public static final String CHANGE_EXPRESSION = "表达式";

    /** 变更项：内容没有变化 */
    public static final String CHANGE_NONE = "无变化";
}
