-- ============================================================
-- ChatVibe 数据库初始化脚本
-- 数据库: MySQL 8.0+
-- 字符集: utf8mb4
-- ============================================================

CREATE DATABASE IF NOT EXISTS `chatvibe` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `chatvibe`;

-- ------------------------------------------------------------
-- 用户表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
                        `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
                        `email`       VARCHAR(100) NOT NULL COMMENT '邮箱(即登录账号)',
                        `password`    VARCHAR(100) NOT NULL COMMENT '密码(BCrypt加密)',
                        `nickname`    VARCHAR(50)           DEFAULT NULL COMMENT '昵称',
                        `avatar`      VARCHAR(255)          DEFAULT NULL COMMENT '头像URL',
                        `bio`         VARCHAR(255)          DEFAULT NULL COMMENT '个人简介',
                        `status`          TINYINT      NOT NULL DEFAULT 0 COMMENT '状态: 0-离线 1-在线 2-忙碌 3-离开',
                        `role`            VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '角色: USER/ADMIN',
                        `notify_desktop`  TINYINT      NOT NULL DEFAULT 1 COMMENT '桌面通知: 0-关闭 1-开启',
                        `notify_sound`    TINYINT      NOT NULL DEFAULT 1 COMMENT '声音通知: 0-关闭 1-开启',
                        `notify_ai_alert` TINYINT      NOT NULL DEFAULT 0 COMMENT 'AI消息提醒: 0-关闭 1-开启',
                        `login_version`   INT          NOT NULL DEFAULT 0 COMMENT '登录版本号: 每次登录递增,用于多设备登录冲突处理',
                        `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                        `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                        `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
                        PRIMARY KEY (`id`),
                        UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ------------------------------------------------------------
-- 会话表 (私聊/群聊/AI)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `conversation`;
CREATE TABLE `conversation` (
                                `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '会话ID',
                                `name`          VARCHAR(100)          DEFAULT NULL COMMENT '会话名称(群聊/AI)',
                                `type`          TINYINT      NOT NULL COMMENT '会话类型: 1-私聊 2-群聊 3-AI',
                                `avatar`        VARCHAR(255)          DEFAULT NULL COMMENT '会话头像',
                                `owner_id`      BIGINT                DEFAULT NULL COMMENT '群主ID(群聊)',
                                `last_message`  VARCHAR(500)          DEFAULT NULL COMMENT '最后一条消息内容(图片/文件已转为预览文本)',
                                `last_message_at` DATETIME            DEFAULT NULL COMMENT '最后消息时间',
                                `last_message_type` TINYINT   NOT NULL DEFAULT 0 COMMENT '最后一条消息类型: 0-文本 1-图片 3-文件 4-系统',
                                `member_count`  INT          NOT NULL DEFAULT 0 COMMENT '成员数',
                                `dissolved`     TINYINT      NOT NULL DEFAULT 0 COMMENT '群组是否已解散: 0-否 1-是（仅群聊有效，成员保留会话但禁言）',
                                `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
                                PRIMARY KEY (`id`),
                                KEY `idx_owner` (`owner_id`),
                                KEY `idx_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话表';

-- ------------------------------------------------------------
-- 会话成员表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `conversation_member`;
CREATE TABLE `conversation_member` (
                                       `id`              BIGINT   NOT NULL AUTO_INCREMENT,
                                       `conversation_id` BIGINT   NOT NULL COMMENT '会话ID',
                                       `user_id`         BIGINT   NOT NULL COMMENT '用户ID',
                                       `role`            TINYINT  NOT NULL DEFAULT 0 COMMENT '角色: 0-成员 1-管理员 2-群主',
                                       `last_read_at`    DATETIME          DEFAULT NULL COMMENT '最后已读时间',
                                       `unread_count`    INT      NOT NULL DEFAULT 0 COMMENT '未读消息数',
                                       `muted`           TINYINT  NOT NULL DEFAULT 0 COMMENT '是否免打扰: 0-否 1-是',
                                       `pinned`          TINYINT  NOT NULL DEFAULT 0 COMMENT '是否置顶: 0-否 1-是',
                                       `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                       `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                       `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
                                       PRIMARY KEY (`id`),
                                       UNIQUE KEY `uk_conv_user` (`conversation_id`, `user_id`),
                                       KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话成员表';

-- ------------------------------------------------------------
-- 消息表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `message`;
CREATE TABLE `message` (
                           `id`              BIGINT       NOT NULL AUTO_INCREMENT,
                           `conversation_id` BIGINT       NOT NULL COMMENT '会话ID',
                           `sender_id`       BIGINT       NOT NULL COMMENT '发送者ID(0表示AI/系统)',
                           `type`            TINYINT      NOT NULL DEFAULT 0 COMMENT '消息类型: 0-文本 1-图片 2-语音 3-文件 4-系统',
                           `content`         TEXT                  DEFAULT NULL COMMENT '消息内容',
                           `extra`           VARCHAR(500)          DEFAULT NULL COMMENT '附加信息(JSON)',
                           `status`          TINYINT      NOT NULL DEFAULT 0 COMMENT '状态: 0-已发送 1-已送达 2-已读',
                           `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                           `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                           `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
                           PRIMARY KEY (`id`),
                           KEY `idx_conv` (`conversation_id`),
                           KEY `idx_sender` (`sender_id`),
                           KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息表';

-- ------------------------------------------------------------
-- 消息隐藏表 (用户级消息删除：仅对操作用户隐藏，其他用户仍可见)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `message_hidden`;
CREATE TABLE `message_hidden` (
                                  `id`          BIGINT   NOT NULL AUTO_INCREMENT,
                                  `user_id`     BIGINT   NOT NULL COMMENT '隐藏消息的用户ID',
                                  `message_id`  BIGINT   NOT NULL COMMENT '被隐藏的消息ID',
                                  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                  `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
                                  PRIMARY KEY (`id`),
                                  UNIQUE KEY `uk_user_msg` (`user_id`, `message_id`),
                                  KEY `idx_user` (`user_id`),
                                  KEY `idx_message` (`message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息隐藏表(用户级)';

-- ------------------------------------------------------------
-- 好友请求表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `friend_request`;
CREATE TABLE `friend_request` (
                                  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
                                  `from_uid`    BIGINT      NOT NULL COMMENT '请求发起者ID',
                                  `to_uid`      BIGINT      NOT NULL COMMENT '接收者ID',
                                  `message`     VARCHAR(255)         DEFAULT NULL COMMENT '验证消息',
                                  `status`      TINYINT     NOT NULL DEFAULT 0 COMMENT '状态: 0-待处理 1-已接受 2-已拒绝',
                                  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                  `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
                                  PRIMARY KEY (`id`),
                                  KEY `idx_to` (`to_uid`),
                                  KEY `idx_from` (`from_uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='好友请求表';

-- ------------------------------------------------------------
-- 群组成员表 (冗余于 conversation_member, 用于群组维度管理)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `group_member`;
CREATE TABLE `group_member` (
                                `id`              BIGINT   NOT NULL AUTO_INCREMENT,
                                `conversation_id` BIGINT   NOT NULL COMMENT '群组会话ID',
                                `user_id`         BIGINT   NOT NULL COMMENT '用户ID',
                                `role`            TINYINT  NOT NULL DEFAULT 0 COMMENT '角色: 0-成员 1-管理员 2-群主',
                                `join_time`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
                                `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
                                PRIMARY KEY (`id`),
                                UNIQUE KEY `uk_group_user` (`conversation_id`, `user_id`),
                                KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群组成员表';

-- ------------------------------------------------------------
-- AI 会话表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `ai_conversation`;
CREATE TABLE `ai_conversation` (
                                   `id`          BIGINT       NOT NULL AUTO_INCREMENT,
                                   `user_id`     BIGINT       NOT NULL COMMENT '用户ID',
                                   `title`       VARCHAR(100)          DEFAULT 'AI 助手' COMMENT '会话标题',
                                   `provider`    VARCHAR(20)  NOT NULL DEFAULT 'ollama' COMMENT 'AI 提供商',
                                   `model`       VARCHAR(50)           DEFAULT NULL COMMENT '模型名',
                                   `context`     TEXT                  DEFAULT NULL COMMENT '上下文(JSON)',
                                   `last_prompt` TEXT                  DEFAULT NULL COMMENT '最后一次提问',
                                   `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                   `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                   `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
                                   PRIMARY KEY (`id`),
                                   KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 会话表';

-- ------------------------------------------------------------
-- 验证码表 (备份记录, 主要使用 Redis)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `verification_code`;
CREATE TABLE `verification_code` (
                                     `id`          BIGINT       NOT NULL AUTO_INCREMENT,
                                     `email`       VARCHAR(100) NOT NULL COMMENT '邮箱',
                                     `code`        VARCHAR(10)  NOT NULL COMMENT '验证码',
                                     `type`        VARCHAR(20)  NOT NULL DEFAULT 'register' COMMENT '类型: register/reset',
                                     `expired`     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否过期',
                                     `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                     `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                     `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
                                     PRIMARY KEY (`id`),
                                     KEY `idx_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='验证码表';


-- ------------------------------------------------------------
-- 消息通知表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '接收通知的用户ID',
    type TINYINT NOT NULL COMMENT '通知类型: 1-系统消息 2-好友请求 3-好友接受 4-好友删除 5-群邀请 6-被移除群 7-群解散',
    title VARCHAR(100) NOT NULL COMMENT '通知标题',
    content VARCHAR(500) COMMENT '通知内容',
    extra VARCHAR(1000) COMMENT '附加数据JSON',
    is_read TINYINT NOT NULL DEFAULT 0 COMMENT '是否已读: 0-未读 1-已读',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_user_read (user_id, is_read, deleted),
    INDEX idx_user_created (user_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息通知表';
