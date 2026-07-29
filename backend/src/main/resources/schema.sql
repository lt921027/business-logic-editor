-- 创建数据库
CREATE DATABASE IF NOT EXISTS business_logic_editor
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE business_logic_editor;

-- 业务逻辑表
CREATE TABLE IF NOT EXISTS business_logic (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    name VARCHAR(255) NOT NULL COMMENT '业务逻辑名称',
    description TEXT COMMENT '描述',
    json_input TEXT COMMENT '入参JSON示例',
    aviator_expression TEXT COMMENT '生成的Aviator表达式',
    groovy_expression TEXT COMMENT '生成的Groovy表达式',
    step_count INT DEFAULT 0 COMMENT '步骤数量',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除标记 0-未删除 1-已删除',
    INDEX idx_name (name),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务逻辑表';

-- 逻辑步骤表
CREATE TABLE IF NOT EXISTS logic_step (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    business_logic_id BIGINT NOT NULL COMMENT '业务逻辑ID',
    step_order INT NOT NULL COMMENT '步骤顺序',
    function_category VARCHAR(50) COMMENT '函数分类 direct/calculation/filter/custom',
    field VARCHAR(500) COMMENT '主字段',
    function_name VARCHAR(100) COMMENT '函数名',
    params TEXT COMMENT '参数JSON',
    custom_expression TEXT COMMENT '自定义表达式',
    output_var VARCHAR(100) COMMENT '输出变量名',
    comment TEXT COMMENT '备注说明',
    filter_scope VARCHAR(500) COMMENT '筛选范围',
    mapped_field VARCHAR(500) COMMENT '映射字段',
    calculation_steps TEXT COMMENT '计算步骤JSON',
    filter_items TEXT COMMENT '筛选条件JSON',
    filter_logic TEXT COMMENT '满足条件时执行JSON',
    reverse_logic TEXT COMMENT '条件不符时执行JSON',
    collapsed TINYINT DEFAULT 0 COMMENT '是否折叠 0-否 1-是',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除标记 0-未删除 1-已删除',
    INDEX idx_business_logic_id (business_logic_id),
    INDEX idx_step_order (step_order),
    FOREIGN KEY (business_logic_id) REFERENCES business_logic(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='逻辑步骤表';
