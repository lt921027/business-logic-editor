    -- 创建缓存数据库
    CREATE DATABASE IF NOT EXISTS business_logic_cache
        DEFAULT CHARACTER SET utf8mb4
        DEFAULT COLLATE utf8mb4_unicode_ci;

    USE business_logic_cache;

    -- 业务逻辑缓存表
    CREATE TABLE IF NOT EXISTS business_logic_cache (
        id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
        business_logic_id BIGINT NOT NULL COMMENT '业务逻辑ID',
        cache_key VARCHAR(255) NOT NULL COMMENT '缓存键',
        cache_value TEXT COMMENT '缓存值（JSON格式）',
        cache_type VARCHAR(50) DEFAULT 'logic' COMMENT '缓存类型：logic/fields/steps/result',
        expire_time DATETIME NOT NULL COMMENT '过期时间',
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
        INDEX idx_business_logic_id (business_logic_id),
        INDEX idx_cache_key (cache_key),
        INDEX idx_expire_time (expire_time),
        UNIQUE KEY uk_business_logic_key (business_logic_id, cache_key)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务逻辑缓存表';

    -- 字段树缓存表
    CREATE TABLE IF NOT EXISTS field_tree_cache (
        id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
        json_input_hash VARCHAR(64) NOT NULL COMMENT 'JSON输入的哈希值',
        field_tree_json TEXT NOT NULL COMMENT '字段树JSON数据',
        expire_time DATETIME NOT NULL COMMENT '过期时间',
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
        INDEX idx_json_input_hash (json_input_hash),
        INDEX idx_expire_time (expire_time),
        UNIQUE KEY uk_json_input_hash (json_input_hash)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字段树缓存表';

    -- 执行结果缓存表
    CREATE TABLE IF NOT EXISTS execution_result_cache (
        id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
        business_logic_id BIGINT NOT NULL COMMENT '业务逻辑ID',
        input_data_hash VARCHAR(64) NOT NULL COMMENT '输入数据的哈希值',
        result_data TEXT COMMENT '执行结果数据',
        execution_time_ms INT COMMENT '执行耗时（毫秒）',
        success TINYINT DEFAULT 1 COMMENT '是否成功：0-失败 1-成功',
        error_message TEXT COMMENT '错误信息',
        expire_time DATETIME NOT NULL COMMENT '过期时间',
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
        INDEX idx_business_logic_id (business_logic_id),
        INDEX idx_input_data_hash (input_data_hash),
        INDEX idx_expire_time (expire_time),
        INDEX idx_success (success),
        UNIQUE KEY uk_business_logic_input (business_logic_id, input_data_hash)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='执行结果缓存表';

    -- 热门业务逻辑表
    CREATE TABLE IF NOT EXISTS hot_business_logic (
        id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
        business_logic_id BIGINT NOT NULL COMMENT '业务逻辑ID',
        access_count INT DEFAULT 1 COMMENT '访问次数',
        last_access_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '最后访问时间',
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
        INDEX idx_business_logic_id (business_logic_id),
        INDEX idx_access_count (access_count),
        INDEX idx_last_access_time (last_access_time),
        UNIQUE KEY uk_business_logic_id (business_logic_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='热门业务逻辑表';

    -- 执行历史表
    CREATE TABLE IF NOT EXISTS execution_history (
        id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
        business_logic_id BIGINT NOT NULL COMMENT '业务逻辑ID',
        input_data TEXT COMMENT '输入数据',
        result_data TEXT COMMENT '执行结果',
        execution_time_ms INT COMMENT '执行耗时（毫秒）',
        success TINYINT DEFAULT 1 COMMENT '是否成功：0-失败 1-成功',
        error_message TEXT COMMENT '错误信息',
        client_ip VARCHAR(50) COMMENT '客户端IP',
        user_agent VARCHAR(500) COMMENT '用户代理',
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
        INDEX idx_business_logic_id (business_logic_id),
        INDEX idx_created_at (created_at),
        INDEX idx_success (success)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='执行历史表';

    -- 缓存配置表
    CREATE TABLE IF NOT EXISTS cache_config (
        id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
        config_key VARCHAR(100) NOT NULL COMMENT '配置键',
        config_value VARCHAR(500) COMMENT '配置值',
        config_type VARCHAR(50) DEFAULT 'string' COMMENT '配置类型：string/number/boolean',
        description TEXT COMMENT '配置描述',
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
        UNIQUE KEY uk_config_key (config_key)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='缓存配置表';

    -- 插入默认缓存配置
    INSERT INTO cache_config (config_key, config_value, config_type, description) VALUES
    ('cache.expire.seconds', '3600', 'number', '缓存过期时间（秒）'),
    ('field_tree.expire.seconds', '86400', 'number', '字段树缓存过期时间（秒）'),
    ('execution_result.expire.seconds', '1800', 'number', '执行结果缓存过期时间（秒）'),
    ('hot_logic.threshold', '10', 'number', '热门业务逻辑阈值'),
    ('max_history.records', '1000', 'number', '最大历史记录数'),
    ('enable.cache', 'true', 'boolean', '是否启用缓存');
