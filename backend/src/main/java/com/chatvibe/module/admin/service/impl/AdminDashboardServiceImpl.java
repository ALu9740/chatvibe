package com.chatvibe.module.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chatvibe.module.admin.service.AdminDashboardService;
import com.chatvibe.module.admin.vo.AdminUserVO;
import com.chatvibe.module.admin.vo.AiUsageTrendVO;
import com.chatvibe.module.admin.vo.AiUsageTrendVO.ProviderBreakdown;
import com.chatvibe.module.admin.vo.DashboardMetricsVO;
import com.chatvibe.module.admin.vo.MessageTrendVO;
import com.chatvibe.module.admin.vo.SystemHealthVO;
import com.chatvibe.module.admin.vo.SystemHealthVO.Metric;
import com.chatvibe.module.admin.vo.SystemHealthVO.MiddlewareHealth;
import com.chatvibe.module.admin.vo.UserGrowthTrendVO;
import com.chatvibe.module.ai.service.AiCallMetricsService;
import com.chatvibe.module.chat.entity.Conversation;
import com.chatvibe.module.chat.entity.Message;
import com.chatvibe.module.chat.mapper.ConversationMapper;
import com.chatvibe.module.chat.mapper.MessageMapper;
import com.chatvibe.module.user.entity.User;
import com.chatvibe.module.user.mapper.UserMapper;
import com.chatvibe.security.LoginUser;
import com.chatvibe.security.SecurityUtils;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

/**
 * 管理后台仪表盘服务实现
 * <p>
 * 所有数据均来自真实查询：
 * - AI 调用次数：统计 message 表中 sender_id=0 的 AI 回复消息
 * - API 可用率/响应时间：从 Redis 中 AiCallMetricsService 记录的调用指标计算
 * - 中间件健康：MySQL 查询、Redis PING、RabbitMQ 连接测试、MinIO bucket 检测
 *
 * @author Alu
 * @date 2026-08-11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MM-dd");
    private static final long AI_SENDER_ID = 0L;
    private static final int AI_MESSAGE_TYPE = 0;
    /** API 可用率/响应时间统计窗口（天） */
    private static final int METRICS_WINDOW_DAYS = 7;

    private final UserMapper userMapper;
    private final MessageMapper messageMapper;
    private final ConversationMapper conversationMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final AiCallMetricsService aiCallMetricsService;
    private final MinioClient minioClient;
    private final DataSource dataSource;

    @Value("${chatvibe.minio.bucket}")
    private String minioBucket;

    /**
     * 获取仪表盘核心指标
     */
    @Override
    public DashboardMetricsVO getMetrics() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        DashboardMetricsVO vo = new DashboardMetricsVO();
        // 总用户数(deleted=0 由 MyBatis-Plus 逻辑删除自动过滤)
        vo.setTotalUsers(userMapper.selectCount(null));
        // 在线用户数(status=1)
        vo.setOnlineUsers(userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getStatus, 1)));
        // 今日新增用户
        vo.setTodayNewUsers(userMapper.selectCount(
                new LambdaQueryWrapper<User>().ge(User::getCreatedAt, todayStart)));
        // 今日消息数
        vo.setTodayMessages(messageMapper.selectCount(
                new LambdaQueryWrapper<Message>().ge(Message::getCreatedAt, todayStart)));
        // 今日 AI 调用次数：统计 message 表中 sender_id=0 的 AI 回复消息数
        vo.setTodayAiCalls(messageMapper.selectCount(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getSenderId, AI_SENDER_ID)
                        .eq(Message::getType, AI_MESSAGE_TYPE)
                        .ge(Message::getCreatedAt, todayStart)));
        // 活跃群组数(type=2 且未解散)
        vo.setActiveGroups(conversationMapper.selectCount(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getType, 2)
                        .eq(Conversation::getDissolved, 0)));
        // API 可用率：从 Redis 中记录的 AI 调用指标计算（最近 7 天）
        vo.setApiAvailability(aiCallMetricsService.getApiAvailability(METRICS_WINDOW_DAYS));
        // 平均响应时间(ms)：从 Redis 中记录的 AI 调用指标计算（最近 7 天）
        vo.setAvgResponseTime(aiCallMetricsService.getAvgResponseTime(METRICS_WINDOW_DAYS));
        return vo;
    }

    /**
     * 获取用户增长趋势
     */
    @Override
    public UserGrowthTrendVO getUserGrowthTrend(int days) {
        UserGrowthTrendVO vo = new UserGrowthTrendVO();
        List<String> dates = new ArrayList<>();
        List<Long> cumulative = new ArrayList<>();

        LocalDate today = LocalDate.now();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime endOfDay = date.atTime(LocalTime.MAX);
            // 累计用户数：创建时间 <= 当天结束
            Long count = userMapper.selectCount(
                    new LambdaQueryWrapper<User>().le(User::getCreatedAt, endOfDay));
            dates.add(date.format(DATE_FMT));
            cumulative.add(count);
        }
        vo.setDates(dates);
        vo.setCumulative(cumulative);
        return vo;
    }

    /**
     * 获取消息趋势
     */
    @Override
    public MessageTrendVO getMessageTrend(int days) {
        MessageTrendVO vo = new MessageTrendVO();
        List<String> dates = new ArrayList<>();
        List<Long> messages = new ArrayList<>();
        List<Long> aiCalls = new ArrayList<>();

        LocalDate today = LocalDate.now();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime startOfDay = date.atStartOfDay();
            LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

            Long msgCount = messageMapper.selectCount(
                    new LambdaQueryWrapper<Message>()
                            .ge(Message::getCreatedAt, startOfDay)
                            .le(Message::getCreatedAt, endOfDay));
            // AI 调用次数：统计当天 sender_id=0 的 AI 回复消息数
            Long aiCount = messageMapper.selectCount(
                    new LambdaQueryWrapper<Message>()
                            .eq(Message::getSenderId, AI_SENDER_ID)
                            .eq(Message::getType, AI_MESSAGE_TYPE)
                            .ge(Message::getCreatedAt, startOfDay)
                            .le(Message::getCreatedAt, endOfDay));
            dates.add(date.format(DATE_FMT));
            messages.add(msgCount);
            aiCalls.add(aiCount);
        }
        vo.setDates(dates);
        vo.setMessages(messages);
        vo.setAiCalls(aiCalls);
        return vo;
    }

    /**
     * 获取 AI 使用趋势
     */
    @Override
    public AiUsageTrendVO getAiUsageTrend(int days) {
        AiUsageTrendVO vo = new AiUsageTrendVO();
        List<String> dates = new ArrayList<>();
        List<Long> calls = new ArrayList<>();

        LocalDateTime startTime = LocalDate.now().minusDays(days - 1L).atStartOfDay();

        // ---- 1. 每日 AI 调用量趋势 ----
        // 独立 try-catch：即使此查询失败，供应商分布仍可正常返回
        Map<LocalDate, Long> dailyMap = new HashMap<>();
        try {
            List<Map<String, Object>> dailyStats = messageMapper.countAiMessagesDaily(startTime);
            for (Map<String, Object> row : dailyStats) {
                Object dateObj = row.get("date");
                Object countObj = row.get("count");
                LocalDate date;
                if (dateObj instanceof java.sql.Date sqlDate) {
                    date = sqlDate.toLocalDate();
                } else if (dateObj instanceof LocalDate localDate) {
                    date = localDate;
                } else {
                    date = LocalDate.parse(dateObj.toString());
                }
                long count = countObj instanceof Number ? ((Number) countObj).longValue() : 0L;
                dailyMap.put(date, count);
            }
        } catch (Exception e) {
            log.error("[Dashboard] 查询每日AI消息统计失败", e);
        }

        // 填充每一天的数据（无数据的日期补 0）
        LocalDate today = LocalDate.now();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            dates.add(date.format(DATE_FMT));
            calls.add(dailyMap.getOrDefault(date, 0L));
        }
        vo.setDates(dates);
        vo.setCalls(calls);

        // ---- 2. 供应商调用分布 ----
        // 独立 try-catch：即使此查询失败，趋势图仍可正常返回
        List<ProviderBreakdown> providerBreakdown = new ArrayList<>();
        try {
            List<Map<String, Object>> providerStats = messageMapper.getAiProviderBreakdown(startTime);
            for (Map<String, Object> row : providerStats) {
                String provider = row.get("provider") == null ? "unknown" : row.get("provider").toString();
                long count = row.get("count") instanceof Number ? ((Number) row.get("count")).longValue() : 0L;
                providerBreakdown.add(new ProviderBreakdown(provider, count));
            }
        } catch (Exception e) {
            log.error("[Dashboard] 查询AI供应商分布失败", e);
        }
        vo.setProviderBreakdown(providerBreakdown);
        return vo;
    }

    /**
     * 获取系统健康状态
     */
    @Override
    public SystemHealthVO getSystemHealth() {
        SystemHealthVO vo = new SystemHealthVO();
        vo.setMysql(checkMysql());
        vo.setRedis(checkRedis());
        vo.setRabbitmq(checkRabbitMQ());
        vo.setMinio(checkMinIO());
        return vo;
    }

    /**
     * 获取当前登录管理员信息
     */
    @Override
    public AdminUserVO getAdminInfo() {
        LoginUser loginUser = SecurityUtils.getCurrentUser();
        AdminUserVO vo = new AdminUserVO();
        vo.setId(loginUser.getId());
        vo.setEmail(loginUser.getEmail());
        vo.setNickname(loginUser.getNickname());
        vo.setAvatar(loginUser.getAvatar());
        String role = loginUser.getRole();
        // 角色透传(管理员为 ADMIN，超管可能为 SUPER_ADMIN)，缺省按管理员处理
        vo.setRole(role != null ? role : "ADMIN");
        // lastLoginAt / lastLoginAt 暂无持久化记录，置为 null
        vo.setLastLoginAt(null);
        vo.setLastLoginIp(null);
        return vo;
    }

    // ==================== 中间件健康检查 ====================

    /**
     * MySQL 健康检查(执行真实查询 + 获取连接信息)
     */
    private MiddlewareHealth checkMysql() {
        List<Metric> metrics = new ArrayList<>();
        String status = "healthy";
        String statusText = "正常";
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            Long userCount = userMapper.selectCount(null);
            metrics.add(new Metric("连接状态", "正常", false));
            metrics.add(new Metric("数据库", metaData.getDatabaseProductName() + " " + metaData.getDatabaseProductVersion(), false));
            metrics.add(new Metric("用户总数", String.valueOf(userCount), false));
        } catch (Exception e) {
            log.warn("MySQL 健康检查失败: {}", e.getMessage());
            status = "error";
            statusText = "异常";
            metrics.add(new Metric("连接状态", "连接失败", true));
            metrics.add(new Metric("错误信息", e.getMessage(), true));
        }
        return new MiddlewareHealth("MySQL", status, statusText, metrics);
    }

    /**
     * Redis 健康检查(执行 PING + 获取连接信息)
     */
    private MiddlewareHealth checkRedis() {
        List<Metric> metrics = new ArrayList<>();
        String status = "healthy";
        String statusText = "正常";
        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
            String pong = new String(connection.ping());
            metrics.add(new Metric("连接状态", "正常", false));
            metrics.add(new Metric("响应", pong, false));
            // 获取 Redis 服务器信息
            try {
                java.util.Properties info = connection.info("server");
                if (info != null) {
                    String version = info.getProperty("redis_version");
                    if (version != null) {
                        metrics.add(new Metric("版本", version, false));
                    }
                }
            } catch (Exception ignored) {
                // info 命令可能因权限受限，忽略
            }
        } catch (Exception e) {
            log.warn("Redis 健康检查失败: {}", e.getMessage());
            status = "error";
            statusText = "异常";
            metrics.add(new Metric("连接状态", "连接失败", true));
            metrics.add(new Metric("错误信息", e.getMessage(), true));
        }
        return new MiddlewareHealth("Redis", status, statusText, metrics);
    }

    /**
     * RabbitMQ 健康检查(执行真实连接测试)
     */
    private MiddlewareHealth checkRabbitMQ() {
        List<Metric> metrics = new ArrayList<>();
        String status = "healthy";
        String statusText = "正常";
        try {
            if (rabbitTemplate == null || rabbitTemplate.getConnectionFactory() == null) {
                status = "warning";
                statusText = "未配置";
                metrics.add(new Metric("连接状态", "未配置", true));
            } else {
                // 创建真实连接并检测是否可用
                org.springframework.amqp.rabbit.connection.Connection conn =
                        rabbitTemplate.getConnectionFactory().createConnection();
                boolean isOpen = conn.isOpen();
                conn.close();
                if (isOpen) {
                    metrics.add(new Metric("连接状态", "正常", false));
                    // 获取已知的交换机名称（从配置中推断）
                    metrics.add(new Metric("交换机", "chat.exchange", false));
                } else {
                    status = "error";
                    statusText = "连接已关闭";
                    metrics.add(new Metric("连接状态", "连接已关闭", true));
                }
            }
        } catch (Exception e) {
            log.warn("RabbitMQ 健康检查失败: {}", e.getMessage());
            status = "error";
            statusText = "异常";
            metrics.add(new Metric("连接状态", "连接失败", true));
            metrics.add(new Metric("错误信息", e.getMessage(), true));
        }
        return new MiddlewareHealth("RabbitMQ", status, statusText, metrics);
    }

    /**
     * MinIO 健康检查(执行真实 bucket 检测)
     */
    private MiddlewareHealth checkMinIO() {
        List<Metric> metrics = new ArrayList<>();
        String status = "healthy";
        String statusText = "正常";
        try {
            // 检测 bucket 是否存在（真实连接测试）
            boolean bucketExists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(minioBucket).build());
            if (bucketExists) {
                metrics.add(new Metric("连接状态", "正常", false));
                metrics.add(new Metric("Bucket", minioBucket, false));
                metrics.add(new Metric("存储", "可用", false));
            } else {
                status = "warning";
                statusText = "Bucket不存在";
                metrics.add(new Metric("连接状态", "正常", false));
                metrics.add(new Metric("Bucket", minioBucket + " (不存在)", true));
            }
        } catch (Exception e) {
            log.warn("MinIO 健康检查失败: {}", e.getMessage());
            status = "error";
            statusText = "异常";
            metrics.add(new Metric("连接状态", "连接失败", true));
            metrics.add(new Metric("错误信息", e.getMessage(), true));
        }
        return new MiddlewareHealth("MinIO", status, statusText, metrics);
    }
}
