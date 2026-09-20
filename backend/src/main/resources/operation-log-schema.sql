-- 操作日志表
--
-- 设计说明：
--   1. 只记录"谁、在什么时间、对哪个对象的哪一类操作、做了什么"，
--      具体业务内容由展示端拿 biz_id 回查对应数据表获得；
--   2. 只记新增/修改/删除三类业务操作，同步、测试用例接口、gRPC 广播、Feign 同步、
--      启动预热、定时轮询一律不记；
--   3. 不加逻辑删除字段，也不参与 MyBatis-Plus 的 logic-delete-field，日志只增不删。

CREATE TABLE IF NOT EXISTS `operation_log` (
    `id`             BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID（兼时序）',
    `biz_type`       VARCHAR(20)  NOT NULL COMMENT '对象类型：SOURCE-源报文 FEATURE-特征配置',
    `biz_id`         VARCHAR(64)  DEFAULT NULL COMMENT '对象ID：源报文编号 或 特征配置ID',
    `operation`      VARCHAR(10)  NOT NULL COMMENT '操作类型：INSERT/UPDATE/DELETE',
    `operation_desc` VARCHAR(500) DEFAULT NULL COMMENT '描述文字，展示端直接显示',
    `success`        TINYINT      DEFAULT 1 COMMENT '是否成功 0-失败 1-成功',
    `error_msg`      VARCHAR(500) DEFAULT NULL COMMENT '失败原因',
    `operator`       VARCHAR(64)  DEFAULT NULL COMMENT '操作人，登记时填写',
    `created_at`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作时间',
    KEY `idx_biz` (`biz_type`, `biz_id`, `id`),
    KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务配置操作日志表';
