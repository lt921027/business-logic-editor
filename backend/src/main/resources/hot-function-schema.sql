-- 热加载函数配置表
CREATE TABLE IF NOT EXISTS `hot_function_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `function_name` VARCHAR(100) NOT NULL COMMENT '函数名称（唯一标识）',
    `function_type` VARCHAR(20) NOT NULL DEFAULT 'expression' COMMENT '函数类型：expression|script|java',
    `content` TEXT COMMENT '表达式内容或脚本内容',
    `class_name` VARCHAR(255) COMMENT 'Java 类全限定名（当 type=java 时使用）',
    `param_names` VARCHAR(500) COMMENT '参数名称（JSON 数组格式）',
    `description` VARCHAR(255) COMMENT '函数描述',
    `version` VARCHAR(50) NOT NULL DEFAULT '1.0' COMMENT '版本号',
    `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用：0-禁用，1-启用',
    `function_group` VARCHAR(50) DEFAULT NULL COMMENT '所属分组',
    `priority` INT NOT NULL DEFAULT 0 COMMENT '优先级（数值越小优先级越高）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by` VARCHAR(50) DEFAULT NULL COMMENT '创建人',
    `update_by` VARCHAR(50) DEFAULT NULL COMMENT '更新人',
    `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_function_name` (`function_name`),
    KEY `idx_function_group` (`function_group`),
    KEY `idx_enabled` (`enabled`),
    KEY `idx_version` (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='热加载函数配置表';

-- 初始数据示例
INSERT INTO `hot_function_config` (`function_name`, `function_type`, `content`, `param_names`, `description`, `version`, `enabled`, `function_group`, `priority`)
VALUES
    ('calculateBonus', 'expression', 'salary * performance * 0.1', '["salary", "performance"]', '计算员工奖金', '1.0', 1, 'salary', 1),
    ('vipDiscount', 'expression', 'price * 0.8', '["price"]', 'VIP会员折扣', '1.0', 1, 'discount', 1),
    ('formatCurrency', 'expression', 'amount / 100.0', '["amount"]', '分转元的货币格式化', '1.0', 1, 'format', 1);
