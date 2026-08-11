package com.chatvibe.module.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * AI 调用指标服务
 * <p>
 * 基于 Redis Hash 记录每次 AI 调用的成功/失败和响应时间，
 * 为管理后台仪表盘提供真实的 API 可用率和平均响应时间数据。
 * <p>
 * Redis 结构：
 * - Key: ai:metrics:{yyyy-MM-dd}  (TTL 30天)
 * - Hash Fields: total, success, failed, totalResponseTimeMs
 *
 * @author Alu
 * @date 2026-08-10
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiCallMetricsService {

    private static final String METRICS_KEY_PREFIX = "ai:metrics:";
    private static final Duration KEY_TTL = Duration.ofDays(30);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final String FIELD_TOTAL = "total";
    private static final String FIELD_SUCCESS = "success";
    private static final String FIELD_FAILED = "failed";
    private static final String FIELD_TOTAL_RESPONSE_MS = "totalResponseTimeMs";

    private final StringRedisTemplate redisTemplate;

    /**
     * 记录一次 AI 调用
     *
     * @param success         是否成功
     * @param responseTimeMs  响应时间(毫秒)
     */
    public void recordCall(boolean success, long responseTimeMs) {
        try {
            String key = METRICS_KEY_PREFIX + LocalDate.now().format(DATE_FMT);
            redisTemplate.opsForHash().increment(key, FIELD_TOTAL, 1);
            if (success) {
                redisTemplate.opsForHash().increment(key, FIELD_SUCCESS, 1);
            } else {
                redisTemplate.opsForHash().increment(key, FIELD_FAILED, 1);
            }
            redisTemplate.opsForHash().increment(key, FIELD_TOTAL_RESPONSE_MS, responseTimeMs);
            redisTemplate.expire(key, KEY_TTL);
        } catch (Exception e) {
            log.warn("[AI指标] 记录调用指标失败: {}", e.getMessage());
        }
    }

    /**
     * 获取今日 AI 调用次数
     */
    public long getTodayCallCount() {
        return getCallCount(LocalDate.now());
    }

    /**
     * 获取指定日期的 AI 调用次数
     */
    public long getCallCount(LocalDate date) {
        try {
            Object total = redisTemplate.opsForHash().get(
                    METRICS_KEY_PREFIX + date.format(DATE_FMT), FIELD_TOTAL);
            return total == null ? 0L : Long.parseLong(total.toString());
        } catch (Exception e) {
            log.warn("[AI指标] 获取调用次数失败: date={}, err={}", date, e.getMessage());
            return 0L;
        }
    }

    /**
     * 获取最近 N 天的 API 可用率(0-100)
     * <p>
     * 可用率 = 成功调用数 / 总调用数 * 100
     * 如果最近 N 天无调用记录，返回 100.0（无故障）
     */
    public double getApiAvailability(int days) {
        long totalCalls = 0;
        long successCalls = 0;
        LocalDate today = LocalDate.now();
        for (int i = 0; i < days; i++) {
            LocalDate date = today.minusDays(i);
            String key = METRICS_KEY_PREFIX + date.format(DATE_FMT);
            try {
                Object total = redisTemplate.opsForHash().get(key, FIELD_TOTAL);
                Object success = redisTemplate.opsForHash().get(key, FIELD_SUCCESS);
                if (total != null) {
                    totalCalls += Long.parseLong(total.toString());
                }
                if (success != null) {
                    successCalls += Long.parseLong(success.toString());
                }
            } catch (Exception e) {
                // 忽略单日读取错误
            }
        }
        if (totalCalls == 0) {
            return 100.0;
        }
        return (double) successCalls / totalCalls * 100.0;
    }

    /**
     * 获取最近 N 天的平均响应时间(ms)
     * <p>
     * 如果最近 N 天无调用记录，返回 0.0
     */
    public double getAvgResponseTime(int days) {
        long totalCalls = 0;
        long totalResponseMs = 0;
        LocalDate today = LocalDate.now();
        for (int i = 0; i < days; i++) {
            LocalDate date = today.minusDays(i);
            String key = METRICS_KEY_PREFIX + date.format(DATE_FMT);
            try {
                Object total = redisTemplate.opsForHash().get(key, FIELD_TOTAL);
                Object responseMs = redisTemplate.opsForHash().get(key, FIELD_TOTAL_RESPONSE_MS);
                if (total != null) {
                    totalCalls += Long.parseLong(total.toString());
                }
                if (responseMs != null) {
                    totalResponseMs += Long.parseLong(responseMs.toString());
                }
            } catch (Exception e) {
                // 忽略单日读取错误
            }
        }
        if (totalCalls == 0) {
            return 0.0;
        }
        return (double) totalResponseMs / totalCalls;
    }
}
