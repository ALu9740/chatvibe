-- ============================================================
-- ChatVibe 数据库初始化脚本
-- 数据库: MySQL 8.0+
-- 字符集: utf8mb4 / 排序规则: utf8mb4_0900_ai_ci
-- ============================================================

CREATE DATABASE IF NOT EXISTS `chatvibe`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;
USE `chatvibe`;

-- ------------------------------------------------------------
-- 用户表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `email`           VARCHAR(255)    NOT NULL COMMENT '邮箱(即登录账号)',
    `password`        VARCHAR(255)    NOT NULL COMMENT '密码(BCrypt加密)',
    `nickname`        VARCHAR(64)              DEFAULT NULL COMMENT '昵称',
    `avatar`          VARCHAR(500)             DEFAULT NULL COMMENT '头像URL',
    `bio`             VARCHAR(255)             DEFAULT NULL COMMENT '个人简介',
    `status`          TINYINT         NOT NULL DEFAULT 0 COMMENT '状态: 0-离线 1-在线 2-忙碌 3-离开',
    `role`            VARCHAR(20)     NOT NULL DEFAULT 'USER' COMMENT '角色: USER-普通用户 OPERATOR-运营员 ADMIN-管理员 SUPER_ADMIN-超级管理员',
    `notify_desktop`  TINYINT         NOT NULL DEFAULT 1 COMMENT '桌面通知: 0-关闭 1-开启',
    `notify_sound`    TINYINT         NOT NULL DEFAULT 1 COMMENT '声音通知: 0-关闭 1-开启',
    `notify_ai_alert` TINYINT         NOT NULL DEFAULT 0 COMMENT 'AI消息提醒: 0-关闭 1-开启',
    `login_version`   INT             NOT NULL DEFAULT 0 COMMENT '登录版本号: 每次登录递增,用于多设备登录冲突处理',
    `banned`          TINYINT         NOT NULL DEFAULT 0 COMMENT '是否被封禁: 0-正常 1-封禁',
    `ban_reason`      VARCHAR(255)             DEFAULT NULL COMMENT '封禁原因',
    `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    -- 生成列: deleted=0 时取 email 值, deleted=1 时为 NULL
    -- 唯一索引建在此列上, 已删除用户的 email 不再占坑, 允许同邮箱重新注册
    `email_active`    VARCHAR(255) AS (CASE WHEN `deleted` = 0 THEN `email` ELSE NULL END) VIRTUAL COMMENT '邮箱唯一约束生成列(仅未删除记录参与)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_email_active` (`email_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';

-- ------------------------------------------------------------
-- 会话表 (私聊/群聊/AI)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `conversation`;
CREATE TABLE `conversation` (
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '会话ID',
    `name`             VARCHAR(128)             DEFAULT NULL COMMENT '会话名称(群聊/AI)',
    `type`             TINYINT         NOT NULL COMMENT '会话类型: 1-私聊 2-群聊 3-AI',
    `avatar`           VARCHAR(500)             DEFAULT NULL COMMENT '会话头像',
    `owner_id`         BIGINT UNSIGNED          DEFAULT NULL COMMENT '群主ID(群聊)',
    `last_message`     VARCHAR(500)             DEFAULT NULL COMMENT '最后一条消息内容(图片/文件已转为预览文本)',
    `last_message_at`  DATETIME                 DEFAULT NULL COMMENT '最后消息时间',
    `last_message_type` TINYINT         NOT NULL DEFAULT 0 COMMENT '最后一条消息类型: 0-文本 1-图片 2-语音 3-文件 4-系统',
    `member_count`     INT             NOT NULL DEFAULT 0 COMMENT '成员数',
    `dissolved`        TINYINT         NOT NULL DEFAULT 0 COMMENT '群组是否已解散: 0-否 1-是（仅群聊有效，成员保留会话但禁言）',
    `ai_provider`      VARCHAR(50)              DEFAULT NULL COMMENT 'AI供应商(最后一次使用的)',
    `ai_model`         VARCHAR(100)             DEFAULT NULL COMMENT 'AI模型(最后一次使用的)',
    `created_at`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`          TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_owner` (`owner_id`),
    KEY `idx_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会话表';

-- ------------------------------------------------------------
-- 会话成员表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `conversation_member`;
CREATE TABLE `conversation_member` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `conversation_id` BIGINT UNSIGNED NOT NULL COMMENT '会话ID',
    `user_id`         BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `role`            TINYINT         NOT NULL DEFAULT 0 COMMENT '角色: 0-成员 1-管理员 2-群主',
    `last_read_at`    DATETIME                 DEFAULT NULL COMMENT '最后已读时间',
    `unread_count`    INT             NOT NULL DEFAULT 0 COMMENT '未读消息数',
    `muted`           TINYINT         NOT NULL DEFAULT 0 COMMENT '是否免打扰: 0-否 1-是',
    `pinned`          TINYINT         NOT NULL DEFAULT 0 COMMENT '是否置顶: 0-否 1-是',
    `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    -- 生成列: deleted=0 时拼接 conversation_id_user_id, deleted=1 时为 NULL
    -- 允许成员退出(deleted=1)后重新加入时恢复或重新插入记录
    `conv_user_active` VARCHAR(50) AS (CASE WHEN `deleted` = 0 THEN CONCAT(`conversation_id`, '_', `user_id`) ELSE NULL END) VIRTUAL COMMENT '会话成员唯一约束生成列(仅未删除记录参与)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_conv_user_active` (`conv_user_active`),
    KEY `idx_user_deleted` (`user_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会话成员表';

-- ------------------------------------------------------------
-- 消息表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `message`;
CREATE TABLE `message` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `conversation_id` BIGINT UNSIGNED NOT NULL COMMENT '会话ID',
    `sender_id`       BIGINT UNSIGNED NOT NULL COMMENT '发送者ID(0表示AI/系统)',
    `type`            TINYINT         NOT NULL DEFAULT 0 COMMENT '消息类型: 0-文本 1-图片 2-语音 3-文件 4-系统',
    `content`         TEXT                     DEFAULT NULL COMMENT '消息内容',
    `extra`           TEXT                     DEFAULT NULL COMMENT '附加信息(JSON): 文件名/大小/尺寸/宽高等',
    `status`          TINYINT         NOT NULL DEFAULT 0 COMMENT '状态: 0-已发送 1-已送达 2-已读',
    `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    -- 复合索引: 会话历史查询总是 conversation_id 过滤 + created_at/id 排序, 避免回表排序
    KEY `idx_conv_created` (`conversation_id`, `created_at`),
    KEY `idx_sender` (`sender_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消息表';

-- ------------------------------------------------------------
-- 消息隐藏表 (用户级消息删除：仅对操作用户隐藏，其他用户仍可见)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `message_hidden`;
CREATE TABLE `message_hidden` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '隐藏消息的用户ID',
    `message_id`  BIGINT UNSIGNED NOT NULL COMMENT '被隐藏的消息ID',
    `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    -- 生成列: 允许用户取消隐藏(deleted=1)后再次隐藏同一条消息
    `user_msg_active` VARCHAR(50) AS (CASE WHEN `deleted` = 0 THEN CONCAT(`user_id`, '_', `message_id`) ELSE NULL END) VIRTUAL COMMENT '消息隐藏唯一约束生成列(仅未删除记录参与)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_msg_active` (`user_msg_active`),
    KEY `idx_user` (`user_id`),
    KEY `idx_message` (`message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消息隐藏表(用户级)';

-- ------------------------------------------------------------
-- 好友请求表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `friend_request`;
CREATE TABLE `friend_request` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `from_uid`    BIGINT UNSIGNED NOT NULL COMMENT '请求发起者ID',
    `to_uid`      BIGINT UNSIGNED NOT NULL COMMENT '接收者ID',
    `message`     VARCHAR(255)             DEFAULT NULL COMMENT '验证消息',
    `status`      TINYINT         NOT NULL DEFAULT 0 COMMENT '状态: 0-待处理 1-已接受 2-已拒绝',
    `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    -- 复合索引: 查询"发给我的待处理请求"高频场景, 覆盖 to_uid + status + deleted 过滤
    KEY `idx_to_status` (`to_uid`, `status`, `deleted`),
    KEY `idx_from` (`from_uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='好友请求表';

-- ------------------------------------------------------------
-- 群组成员表 (冗余于 conversation_member, 用于群组维度管理)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `group_member`;
CREATE TABLE `group_member` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `conversation_id` BIGINT UNSIGNED NOT NULL COMMENT '群组会话ID',
    `user_id`         BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `role`            TINYINT         NOT NULL DEFAULT 0 COMMENT '角色: 0-成员 1-管理员 2-群主',
    `join_time`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    -- 生成列: 允许成员被移除(deleted=1)后重新被邀请加入
    `group_user_active` VARCHAR(50) AS (CASE WHEN `deleted` = 0 THEN CONCAT(`conversation_id`, '_', `user_id`) ELSE NULL END) VIRTUAL COMMENT '群成员唯一约束生成列(仅未删除记录参与)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_group_user_active` (`group_user_active`),
    KEY `idx_user_deleted` (`user_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='群组成员表';

-- ------------------------------------------------------------
-- AI 会话表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `ai_conversation`;
CREATE TABLE `ai_conversation` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `title`       VARCHAR(128)             DEFAULT 'AI 助手' COMMENT '会话标题',
    `provider`    VARCHAR(50)     NOT NULL DEFAULT 'ollama' COMMENT 'AI 提供商: ollama/openai/qwen等',
    `model`       VARCHAR(100)             DEFAULT NULL COMMENT '模型名',
    `context`     MEDIUMTEXT               DEFAULT NULL COMMENT '上下文(JSON): ChatMemory 存储, 20条消息窗口',
    `last_prompt` TEXT                     DEFAULT NULL COMMENT '最后一次提问',
    `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 会话表';

-- ------------------------------------------------------------
-- 消息通知表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `notification`;
CREATE TABLE `notification` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT UNSIGNED NOT NULL COMMENT '接收通知的用户ID',
    `type`       TINYINT         NOT NULL COMMENT '通知类型: 1-系统消息 2-好友请求 3-好友接受 4-好友删除 5-群邀请 6-被移除群 7-群解散 8-群转让',
    `title`      VARCHAR(100)    NOT NULL COMMENT '通知标题',
    `content`    VARCHAR(500)             DEFAULT NULL COMMENT '通知内容',
    `extra`      TEXT                     DEFAULT NULL COMMENT '附加数据JSON: 群邀请含成员列表等',
    `is_read`    TINYINT         NOT NULL DEFAULT 0 COMMENT '是否已读: 0-未读 1-已读',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`    TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_read` (`user_id`, `is_read`, `deleted`),
    KEY `idx_user_created` (`user_id`, `created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消息通知表';

-- ------------------------------------------------------------
-- 操作日志表 (管理员操作审计)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `operation_log`;
CREATE TABLE `operation_log` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `operator_id`     BIGINT UNSIGNED NOT NULL COMMENT '操作者ID',
    `operator_email`  VARCHAR(255)    NOT NULL COMMENT '操作者邮箱',
    `type`            VARCHAR(50)     NOT NULL COMMENT '操作类型: LOGIN/USER_BAN/USER_UNBAN/ROLE_CHANGE/PASSWORD_RESET/MESSAGE_DELETE/GROUP_DISSOLVE/GROUP_TRANSFER/ANNOUNCEMENT_PUBLISH/ANNOUNCEMENT_WITHDRAW/RATE_LIMIT_CONFIG/CIRCUIT_BREAKER_CONFIG/CACHE_CLEAR/ADMIN_ACCOUNT_MANAGE',
    `detail`          VARCHAR(500)             DEFAULT NULL COMMENT '操作详情',
    `ip`              VARCHAR(50)              DEFAULT NULL COMMENT '操作IP',
    `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_operator` (`operator_id`),
    KEY `idx_type` (`type`),
    KEY `idx_created` (`created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作日志表';

-- ------------------------------------------------------------
-- 公告表 (管理员发布系统公告)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `announcement`;
CREATE TABLE `announcement` (
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `title`        VARCHAR(200)    NOT NULL COMMENT '公告标题',
    `content`      TEXT            NOT NULL COMMENT '公告内容',
    `scope`        VARCHAR(20)     NOT NULL DEFAULT 'all' COMMENT '范围: all-全部用户 specified-指定用户',
    `target_count` INT             NOT NULL DEFAULT 0 COMMENT '目标用户数',
    `status`       VARCHAR(20)     NOT NULL DEFAULT 'published' COMMENT '状态: published-已发布 withdrawn-已撤回',
    `created_by`   VARCHAR(255)    NOT NULL COMMENT '创建者邮箱',
    `created_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`),
    KEY `idx_created` (`created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公告表';

-- ------------------------------------------------------------
-- AI 供应商配置表 (管理员后台动态管理 AI 服务供应商)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `ai_provider`;
CREATE TABLE `ai_provider` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `name`          VARCHAR(50)     NOT NULL COMMENT '供应商标识(如 qwen/openai/ollama)',
    `type`          VARCHAR(20)     NOT NULL DEFAULT 'cloud' COMMENT '类型: local-本地 cloud-云端',
    `model`         VARCHAR(100)    NOT NULL COMMENT '模型名',
    `base_url`      VARCHAR(500)    NOT NULL COMMENT 'API base-url',
    `api_key`       VARCHAR(500)             DEFAULT NULL COMMENT 'API密钥(local 可为空)',
    `status`        VARCHAR(20)     NOT NULL DEFAULT 'offline' COMMENT '状态: online/offline/checking',
    `latency`       INT             NOT NULL DEFAULT 0 COMMENT '最近一次测试延迟(ms)',
    `priority`      INT             NOT NULL DEFAULT 0 COMMENT '故障转移优先级(数字越小优先级越高)',
    `enabled`       TINYINT         NOT NULL DEFAULT 1 COMMENT '是否启用: 0-禁用 1-启用',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI供应商配置表';

-- ------------------------------------------------------------
-- 系统配置表 (管理员后台动态管理系统参数: 限流/熔断/邮件等)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `system_config`;
CREATE TABLE `system_config` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `config_key`  VARCHAR(100)    NOT NULL COMMENT '配置键',
    `config_value` TEXT           NOT NULL COMMENT '配置值(JSON)',
    `description` VARCHAR(255)             DEFAULT NULL COMMENT '配置描述',
    `updated_by`  VARCHAR(255)             DEFAULT NULL COMMENT '最后更新者邮箱',
    `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统配置表';
