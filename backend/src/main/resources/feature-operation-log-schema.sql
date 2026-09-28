-- 特征操作日志表
--
-- 设计说明：
--   1. 只记录特征（business_logic + logic_step）的新增、修改、删除；
--      同步、测试接口、gRPC 广播、Feign 同步、启动预热、定时轮询一律不记；
--   2. 修改成功时同时保留修改前后的 Groovy 表达式与步骤快照，
--      改动前查库获得，改动后在业务事务提交后查库获得；
--   3. operator 取值逻辑由业务自行补充，切面只预留字段；
--   4. 不加逻辑删除字段，也不参与 MyBatis-Plus 的 logic-delete-field，日志只增不删。

CREATE TABLE IF NOT EXISTS `feature_operation_log` (
    `id`                     BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID（兼时序）',
    `feature_id`             BIGINT       DEFAULT NULL COMMENT '特征ID（business_logic.id），新增失败时为空',
    `operation`              VARCHAR(10)  NOT NULL COMMENT '操作类型：INSERT/UPDATE/DELETE',
    `expression_before`      MEDIUMTEXT   DEFAULT NULL COMMENT '修改前 Groovy 表达式',
    `expression_after`       MEDIUMTEXT   DEFAULT NULL COMMENT '修改后 Groovy 表达式',
    `expression_hash_before` VARCHAR(32)  DEFAULT NULL COMMENT '修改前表达式 MD5',
    `expression_hash_after`  VARCHAR(32)  DEFAULT NULL COMMENT '修改后表达式 MD5',
    `step_data_before`       MEDIUMTEXT   DEFAULT NULL COMMENT '修改前步骤（LogicStep 实体，驼峰JSON）',
    `step_data_after`        MEDIUMTEXT   DEFAULT NULL COMMENT '修改后步骤（LogicStep 实体，驼峰JSON）',
    `changed_fields`         VARCHAR(100) DEFAULT NULL COMMENT '变更项：步骤/表达式/无变化，仅修改成功时填写',
    `request_snapshot`       MEDIUMTEXT   DEFAULT NULL COMMENT '请求入参快照',
    `operator`               VARCHAR(64)  DEFAULT NULL COMMENT '操作人，取值逻辑由业务自行补充',
    `success`                TINYINT      DEFAULT 1 COMMENT '是否成功 0-失败 1-成功',
    `error_msg`              VARCHAR(500) DEFAULT NULL COMMENT '失败原因',
    `created_at`             DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作时间',
    KEY `idx_feature` (`feature_id`, `id`),
    KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='特征操作日志表';
