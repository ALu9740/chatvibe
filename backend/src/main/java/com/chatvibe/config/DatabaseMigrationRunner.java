package com.chatvibe.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

/**
 * 数据库迁移自动执行器
 * 应用启动时检查并执行必要的表结构迁移，确保新旧数据库兼容
 *
 * @author Alu
 * @date 2026-08-07
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class DatabaseMigrationRunner implements CommandLineRunner {

    private final DataSource dataSource;

    @Override
    public void run(String... args) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        // 1. notification 表添加 announcement_id 列（支持公告撤回时批量删除关联通知）
        ensureAnnouncementIdColumn(jdbcTemplate);

        // 2. notification.content 列改为 TEXT（支持公告正文 ≤5000 字符）
        ensureContentColumnText(jdbcTemplate);

        // 3. notification 表创建 idx_announcement 索引
        ensureAnnouncementIndex(jdbcTemplate);

        // 4. announcement.title 列长度调整为 VARCHAR(100)（对齐 PRD ≤100 字符约束）
        ensureAnnouncementTitleLength(jdbcTemplate);

        log.info("[迁移] 数据库迁移检查完成");
    }

    /**
     * 检查并添加 announcement_id 列到 notification 表
     */
    private void ensureAnnouncementIdColumn(JdbcTemplate jdbcTemplate) {
        try {
            List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                    "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notification' AND COLUMN_NAME = 'announcement_id'"
            );
            if (columns.isEmpty()) {
                jdbcTemplate.execute("ALTER TABLE `notification` ADD COLUMN `announcement_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联公告ID(系统通知type=1时填写)' AFTER `extra`");
                log.info("[迁移] notification 表添加 announcement_id 列成功");
            }
        } catch (Exception e) {
            log.warn("[迁移] 检查/添加 announcement_id 列失败: {}", e.getMessage());
        }
    }

    /**
     * 检查并将 notification.content 列改为 TEXT 类型
     */
    private void ensureContentColumnText(JdbcTemplate jdbcTemplate) {
        try {
            List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                    "SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notification' AND COLUMN_NAME = 'content'"
            );
            if (!columns.isEmpty()) {
                String dataType = (String) columns.get(0).get("DATA_TYPE");
                if (!"text".equalsIgnoreCase(dataType)) {
                    jdbcTemplate.execute("ALTER TABLE `notification` MODIFY COLUMN `content` TEXT DEFAULT NULL COMMENT '通知内容(支持公告长文本)'");
                    log.info("[迁移] notification.content 列类型改为 TEXT 成功");
                }
            }
        } catch (Exception e) {
            log.warn("[迁移] 检查/修改 content 列类型失败: {}", e.getMessage());
        }
    }

    /**
     * 检查并创建 idx_announcement 索引
     */
    private void ensureAnnouncementIndex(JdbcTemplate jdbcTemplate) {
        try {
            List<Map<String, Object>> indexes = jdbcTemplate.queryForList(
                    "SELECT INDEX_NAME FROM INFORMATION_SCHEMA.STATISTICS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notification' AND INDEX_NAME = 'idx_announcement'"
            );
            if (indexes.isEmpty()) {
                jdbcTemplate.execute("CREATE INDEX `idx_announcement` ON `notification` (`announcement_id`)");
                log.info("[迁移] notification 表创建 idx_announcement 索引成功");
            }
        } catch (Exception e) {
            log.warn("[迁移] 检查/创建 idx_announcement 索引失败: {}", e.getMessage());
        }
    }

    /**
     * 检查并调整 announcement.title 列长度为 VARCHAR(100)
     */
    private void ensureAnnouncementTitleLength(JdbcTemplate jdbcTemplate) {
        try {
            List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                    "SELECT COLUMN_TYPE FROM INFORMATION_SCHEMA.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'announcement' AND COLUMN_NAME = 'title'"
            );
            if (!columns.isEmpty()) {
                String columnType = (String) columns.get(0).get("COLUMN_TYPE");
                if (!"varchar(100)".equalsIgnoreCase(columnType)) {
                    jdbcTemplate.execute("ALTER TABLE `announcement` MODIFY COLUMN `title` VARCHAR(100) NOT NULL COMMENT '公告标题'");
                    log.info("[迁移] announcement.title 列长度调整为 VARCHAR(100) 成功");
                }
            }
        } catch (Exception e) {
            log.warn("[迁移] 检查/修改 announcement.title 列长度失败: {}", e.getMessage());
        }
    }
}
