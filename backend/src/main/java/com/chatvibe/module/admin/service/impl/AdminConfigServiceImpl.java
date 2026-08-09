package com.chatvibe.module.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chatvibe.common.exception.BusinessException;
import com.chatvibe.common.result.ResultCode;
import com.chatvibe.config.DynamicMailSenderProvider;
import com.chatvibe.module.admin.entity.SystemConfig;
import com.chatvibe.module.admin.enums.OperationTypeEnum;
import com.chatvibe.module.admin.mapper.SystemConfigMapper;
import com.chatvibe.module.admin.service.AdminConfigService;
import com.chatvibe.module.admin.service.AdminLogService;
import com.chatvibe.module.admin.vo.CacheStatVO;
import com.chatvibe.module.admin.vo.CircuitBreakerConfigVO;
import com.chatvibe.module.admin.vo.EmailConfigVO;
import com.chatvibe.module.admin.vo.RateLimiterConfigVO;
import com.chatvibe.security.LoginUser;
import com.chatvibe.security.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 管理员系统配置服务实现
 * 管理限流器、熔断器、缓存和邮件配置
 *
 * 核心机制：
 * 1. DB-注册表双向同步：写入时同时保存DB和更新注册表，读取时合并DB覆盖值
 * 2. 启动时从DB加载已保存的配置覆盖到注册表，确保重启后配置不丢失
 * 3. 参数范围校验 + 5分钟冷却期 + 权限检查
 *
 * @author Alu
 * @date 2026-08-09
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminConfigServiceImpl implements AdminConfigService {

    private static final String CONFIG_KEY_RATE_LIMITER_PREFIX = "rate_limiter.";
    private static final String CONFIG_KEY_CIRCUIT_BREAKER_PREFIX = "circuit_breaker.";
    private static final String CONFIG_KEY_EMAIL = "email_config";

    private static final String COOLDOWN_PREFIX = "config:cooldown:";
    private static final long COOLDOWN_SECONDS = 300; // 5分钟

    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    private static final String ROLE_ADMIN = "ADMIN";

    private static final Map<String, String> CACHE_TTL_MAP = Map.of(
            "userInfo", "5min",
            "userEmail", "5min",
            "emailExists", "5min",
            "userNotifyPrefs", "5min",
            "userSearch", "1min",
            "friendList", "5min",
            "groupDetail", "3min",
            "groupMembers", "5min",
            "notifUnreadCount", "30s"
    );
    private static final String DEFAULT_TTL = "various";

    private final RateLimiterRegistry rateLimiterRegistry;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final CacheManager cacheManager;
    private final SystemConfigMapper systemConfigMapper;
    private final AdminLogService adminLogService;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final DynamicMailSenderProvider mailSenderProvider;

    // ==================== 启动加载 ====================

    /**
     * 启动时从DB加载已保存的配置，应用到Resilience4j注册表
     * 确保服务器重启后管理员修改的配置仍然生效
     */
    @PostConstruct
    public void loadConfigsFromDb() {
        int rateCount = loadRateLimiterConfigsFromDb();
        int cbCount = loadCircuitBreakerConfigsFromDb();
        log.info("[配置] 启动加载DB配置完成: 限流器={}个, 熔断器={}个", rateCount, cbCount);
    }

    private int loadRateLimiterConfigsFromDb() {
        List<SystemConfig> configs = systemConfigMapper.selectList(
                new LambdaQueryWrapper<SystemConfig>()
                        .likeRight(SystemConfig::getConfigKey, CONFIG_KEY_RATE_LIMITER_PREFIX));
        int count = 0;
        for (SystemConfig config : configs) {
            try {
                RateLimiterConfigVO vo = objectMapper.readValue(config.getConfigValue(), RateLimiterConfigVO.class);
                String name = config.getConfigKey().substring(CONFIG_KEY_RATE_LIMITER_PREFIX.length());
                applyRateLimiterConfig(name, vo);
                count++;
            } catch (Exception e) {
                log.warn("[配置] 启动加载限流器配置失败: key={}, err={}", config.getConfigKey(), e.getMessage());
            }
        }
        return count;
    }

    private int loadCircuitBreakerConfigsFromDb() {
        List<SystemConfig> configs = systemConfigMapper.selectList(
                new LambdaQueryWrapper<SystemConfig>()
                        .likeRight(SystemConfig::getConfigKey, CONFIG_KEY_CIRCUIT_BREAKER_PREFIX));
        int count = 0;
        for (SystemConfig config : configs) {
            try {
                CircuitBreakerConfigVO vo = objectMapper.readValue(config.getConfigValue(), CircuitBreakerConfigVO.class);
                String name = config.getConfigKey().substring(CONFIG_KEY_CIRCUIT_BREAKER_PREFIX.length());
                applyCircuitBreakerConfig(name, vo);
                count++;
            } catch (Exception e) {
                log.warn("[配置] 启动加载熔断器配置失败: key={}, err={}", config.getConfigKey(), e.getMessage());
            }
        }
        return count;
    }

    // ==================== 限流器配置 ====================

    @Override
    public List<RateLimiterConfigVO> getRateLimiters() {
        // 1. 从注册表获取所有限流器的当前运行时配置
        Map<String, RateLimiterConfigVO> resultMap = new LinkedHashMap<>();
        rateLimiterRegistry.getAllRateLimiters().forEach(rateLimiter -> {
            RateLimiterConfigVO vo = new RateLimiterConfigVO();
            vo.setName(rateLimiter.getName());
            vo.setLimitForPeriod(rateLimiter.getRateLimiterConfig().getLimitForPeriod());
            vo.setLimitRefreshPeriod(durationToString(rateLimiter.getRateLimiterConfig().getLimitRefreshPeriod()));
            vo.setTimeoutDuration(durationToString(rateLimiter.getRateLimiterConfig().getTimeoutDuration()));
            resultMap.put(rateLimiter.getName(), vo);
        });

        // 2. 从DB加载已保存的覆盖配置，合并到结果中
        Map<String, RateLimiterConfigVO> dbOverrides = loadRateLimiterOverridesFromDb();
        for (Map.Entry<String, RateLimiterConfigVO> entry : dbOverrides.entrySet()) {
            if (resultMap.containsKey(entry.getKey())) {
                RateLimiterConfigVO dbVo = entry.getValue();
                RateLimiterConfigVO result = resultMap.get(entry.getKey());
                // DB保存的配置覆盖注册表默认值
                result.setLimitForPeriod(dbVo.getLimitForPeriod());
                result.setLimitRefreshPeriod(dbVo.getLimitRefreshPeriod());
                result.setTimeoutDuration(dbVo.getTimeoutDuration());
            }
        }

        return resultMap.values().stream()
                .sorted(Comparator.comparing(RateLimiterConfigVO::getName))
                .collect(Collectors.toList());
    }

    @Override
    public boolean updateRateLimiter(String name, RateLimiterConfigVO config) {
        // 权限检查：仅 SUPER_ADMIN 可修改
        requireSuperAdmin();

        // 参数范围校验
        validateRateLimiterConfig(config);

        // 冷却期检查
        checkCooldown("rate_limiter", name);

        // 获取修改前的值（用于操作日志）
        RateLimiterConfigVO beforeValue = getRateLimiterCurrentConfig(name);

        // 保存到DB
        String configKey = CONFIG_KEY_RATE_LIMITER_PREFIX + name;
        String configValue = toJson(config);
        String updatedBy = SecurityUtils.getCurrentEmail();
        upsertSystemConfig(configKey, configValue, "限流器配置: " + name, updatedBy);

        // 应用到注册表（实时生效）
        applyRateLimiterConfig(name, config);

        // 设置冷却期
        setCooldown("rate_limiter", name);

        // 记录操作日志（含修改前后的值）
        String logDetail = String.format("修改限流配置[%s]: limitForPeriod %d→%d, limitRefreshPeriod %s→%s, timeoutDuration %s→%s",
                name,
                beforeValue.getLimitForPeriod(), config.getLimitForPeriod(),
                beforeValue.getLimitRefreshPeriod(), config.getLimitRefreshPeriod(),
                beforeValue.getTimeoutDuration(), config.getTimeoutDuration());
        adminLogService.log(OperationTypeEnum.RATE_LIMIT_CONFIG, logDetail);

        log.info("[配置] 修改限流器配置: name={}, updatedBy={}", name, updatedBy);
        return true;
    }

    // ==================== 熔断器配置 ====================

    @Override
    public List<CircuitBreakerConfigVO> getCircuitBreakers() {
        // 1. 从注册表获取所有熔断器的当前运行时配置
        Map<String, CircuitBreakerConfigVO> resultMap = new LinkedHashMap<>();
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(circuitBreaker -> {
            CircuitBreakerConfigVO vo = new CircuitBreakerConfigVO();
            vo.setName(circuitBreaker.getName());
            vo.setFailureRateThreshold((double) circuitBreaker.getCircuitBreakerConfig().getFailureRateThreshold());
            vo.setSlowCallRateThreshold((double) circuitBreaker.getCircuitBreakerConfig().getSlowCallRateThreshold());
            vo.setSlowCallDurationThreshold(durationToString(circuitBreaker.getCircuitBreakerConfig().getSlowCallDurationThreshold()));
            vo.setWaitDurationInOpenState(durationToString(Duration.ofMillis(circuitBreaker.getCircuitBreakerConfig().getWaitIntervalFunctionInOpenState().apply(1))));
            vo.setPermittedNumberOfCallsInHalfOpenState(circuitBreaker.getCircuitBreakerConfig().getPermittedNumberOfCallsInHalfOpenState());
            resultMap.put(circuitBreaker.getName(), vo);
        });

        // 2. 从DB加载已保存的覆盖配置，合并到结果中
        Map<String, CircuitBreakerConfigVO> dbOverrides = loadCircuitBreakerOverridesFromDb();
        for (Map.Entry<String, CircuitBreakerConfigVO> entry : dbOverrides.entrySet()) {
            if (resultMap.containsKey(entry.getKey())) {
                CircuitBreakerConfigVO dbVo = entry.getValue();
                CircuitBreakerConfigVO result = resultMap.get(entry.getKey());
                result.setFailureRateThreshold(dbVo.getFailureRateThreshold());
                result.setSlowCallRateThreshold(dbVo.getSlowCallRateThreshold());
                result.setSlowCallDurationThreshold(dbVo.getSlowCallDurationThreshold());
                result.setWaitDurationInOpenState(dbVo.getWaitDurationInOpenState());
                result.setPermittedNumberOfCallsInHalfOpenState(dbVo.getPermittedNumberOfCallsInHalfOpenState());
            }
        }

        return resultMap.values().stream()
                .sorted(Comparator.comparing(CircuitBreakerConfigVO::getName))
                .collect(Collectors.toList());
    }

    @Override
    public boolean updateCircuitBreaker(String name, CircuitBreakerConfigVO config) {
        // 权限检查：仅 SUPER_ADMIN 可修改
        requireSuperAdmin();

        // 参数范围校验
        validateCircuitBreakerConfig(config);

        // 冷却期检查
        checkCooldown("circuit_breaker", name);

        // 获取修改前的值（用于操作日志）
        CircuitBreakerConfigVO beforeValue = getCircuitBreakerCurrentConfig(name);

        // 保存到DB
        String configKey = CONFIG_KEY_CIRCUIT_BREAKER_PREFIX + name;
        String configValue = toJson(config);
        String updatedBy = SecurityUtils.getCurrentEmail();
        upsertSystemConfig(configKey, configValue, "熔断器配置: " + name, updatedBy);

        // 应用到注册表（实时生效）
        applyCircuitBreakerConfig(name, config);

        // 设置冷却期
        setCooldown("circuit_breaker", name);

        // 记录操作日志（含修改前后的值）
        String logDetail = String.format("修改熔断配置[%s]: failureRate %.0f→%.0f, slowCallRate %.0f→%.0f, slowCallDuration %s→%s, waitDuration %s→%s, halfOpenCalls %d→%d",
                name,
                beforeValue.getFailureRateThreshold(), config.getFailureRateThreshold(),
                beforeValue.getSlowCallRateThreshold(), config.getSlowCallRateThreshold(),
                beforeValue.getSlowCallDurationThreshold(), config.getSlowCallDurationThreshold(),
                beforeValue.getWaitDurationInOpenState(), config.getWaitDurationInOpenState(),
                beforeValue.getPermittedNumberOfCallsInHalfOpenState(), config.getPermittedNumberOfCallsInHalfOpenState());
        adminLogService.log(OperationTypeEnum.CIRCUIT_BREAKER_CONFIG, logDetail);

        log.info("[配置] 修改熔断器配置: name={}, updatedBy={}", name, updatedBy);
        return true;
    }

    // ==================== 缓存管理 ====================

    @Override
    public List<CacheStatVO> getCacheStats() {
        // 权限检查：SUPER_ADMIN 和 ADMIN 可查看
        requireAdminOrAbove();

        List<CacheStatVO> list = new ArrayList<>();
        for (String cacheName : cacheManager.getCacheNames()) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache == null) {
                continue;
            }
            CacheStatVO vo = new CacheStatVO();
            vo.setName(cacheName);
            vo.setTtl(CACHE_TTL_MAP.getOrDefault(cacheName, DEFAULT_TTL));

            if (cache instanceof CaffeineCache caffeineCache) {
                com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache =
                        caffeineCache.getNativeCache();
                try {
                    vo.setHitRate(nativeCache.stats().hitRate() * 100);
                    vo.setSize(nativeCache.estimatedSize());
                } catch (Exception e) {
                    vo.setHitRate(0.0);
                    vo.setSize(0L);
                }
            } else {
                vo.setHitRate(0.0);
                vo.setSize(0L);
            }
            list.add(vo);
        }
        return list.stream()
                .sorted(Comparator.comparing(CacheStatVO::getName))
                .collect(Collectors.toList());
    }

    @Override
    public boolean clearCache(String name) {
        // 权限检查：仅 SUPER_ADMIN 可清除
        requireSuperAdmin();

        Cache cache = cacheManager.getCache(name);
        if (cache != null) {
            cache.clear();
            log.info("[配置] 清除缓存: name={}", name);
        } else {
            log.warn("[配置] 缓存不存在，无法清除: name={}", name);
        }
        adminLogService.log(OperationTypeEnum.CACHE_CLEAR, "清除缓存: " + name);
        return true;
    }

    // ==================== 邮件配置 ====================

    private static final String MASKED_PASSWORD = "******";

    @Override
    public EmailConfigVO getEmailConfig() {
        // 权限检查：仅 SUPER_ADMIN 可查看
        requireSuperAdmin();

        SystemConfig config = systemConfigMapper.selectOne(
                new LambdaQueryWrapper<SystemConfig>()
                        .eq(SystemConfig::getConfigKey, CONFIG_KEY_EMAIL));
        if (config != null && config.getConfigValue() != null) {
            try {
                EmailConfigVO vo = objectMapper.readValue(config.getConfigValue(), EmailConfigVO.class);
                // 密码脱敏：不将明文密码返回给前端
                if (vo.getPassword() != null && !vo.getPassword().isBlank()) {
                    vo.setPassword(MASKED_PASSWORD);
                }
                return vo;
            } catch (Exception e) {
                log.warn("[配置] 解析邮件配置JSON失败: {}", e.getMessage());
            }
        }
        return new EmailConfigVO();
    }

    @Override
    public boolean updateEmailConfig(EmailConfigVO config) {
        // 权限检查：仅 SUPER_ADMIN 可修改
        requireSuperAdmin();

        // 参数校验
        if (config.getHost() == null || config.getHost().isBlank()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "邮件服务器地址不能为空");
        }
        if (config.getPort() == null || config.getPort() < 1 || config.getPort() > 65535) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "邮件端口范围: 1-65535");
        }
        if (config.getUsername() == null || config.getUsername().isBlank()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "SMTP用户名不能为空");
        }

        // 密码脱敏处理：如果前端传回 ******，保留DB中的原密码
        if (MASKED_PASSWORD.equals(config.getPassword())) {
            EmailConfigVO existing = loadEmailConfigFromDb();
            if (existing != null) {
                config.setPassword(existing.getPassword());
            }
        }

        // SSL 默认值
        if (config.getSslEnabled() == null) {
            config.setSslEnabled(config.getPort() != null && config.getPort() == 465);
        }

        String configValue = toJson(config);
        String updatedBy = SecurityUtils.getCurrentEmail();
        upsertSystemConfig(CONFIG_KEY_EMAIL, configValue, "邮件配置", updatedBy);

        // 动态重建 JavaMailSender，实时生效
        mailSenderProvider.reload(config);

        log.info("[配置] 更新邮件配置: updatedBy={}", updatedBy);
        adminLogService.log(OperationTypeEnum.EMAIL_CONFIG, "更新邮件配置: " + config.getHost() + ":" + config.getPort());
        return true;
    }

    @Override
    public boolean sendTestEmail(EmailConfigVO config) {
        // 权限检查：仅 SUPER_ADMIN 可操作
        requireSuperAdmin();

        // 参数校验
        if (config.getHost() == null || config.getHost().isBlank()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "SMTP 服务器地址不能为空");
        }
        if (config.getPort() == null || config.getPort() < 1 || config.getPort() > 65535) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "端口范围: 1-65535");
        }
        if (config.getUsername() == null || config.getUsername().isBlank()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "SMTP 用户名不能为空");
        }

        // 密码脱敏处理：如果前端传回 ******，从 DB 加载真实密码
        if (MASKED_PASSWORD.equals(config.getPassword())) {
            EmailConfigVO existing = loadEmailConfigFromDb();
            if (existing != null && existing.getPassword() != null) {
                config.setPassword(existing.getPassword());
            } else {
                throw new BusinessException(ResultCode.PARAM_INVALID, "请先填写 SMTP 授权码/密码");
            }
        }
        if (config.getPassword() == null || config.getPassword().isBlank()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "SMTP 授权码/密码不能为空");
        }

        // SSL 默认值
        if (config.getSslEnabled() == null) {
            config.setSslEnabled(config.getPort() == 465);
        }

        String adminEmail = SecurityUtils.getCurrentUser().getEmail();
        String fromAddress = DynamicMailSenderProvider.resolveFromAddress(config);

        try {
            // 使用前端传入的配置创建临时 JavaMailSender（不修改运行时 sender）
            JavaMailSenderImpl testSender = mailSenderProvider.createMailSender(config);

            MimeMessage mimeMessage = testSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(adminEmail);
            helper.setSubject("【ChatVibe】测试邮件");
            helper.setText("这是一封来自 ChatVibe 管理后台的测试邮件，用于验证邮件服务器配置是否正常。<br><br>"
                    + "SMTP 服务器: " + config.getHost() + ":" + config.getPort() + "<br>"
                    + "发件账号: " + config.getUsername() + "<br>"
                    + "SSL 加密: " + (Boolean.TRUE.equals(config.getSslEnabled()) ? "已开启" : "未开启"), true);
            testSender.send(mimeMessage);
            log.info("[配置] 测试邮件发送成功: host={}, port={}, from={}, to={}",
                    config.getHost(), config.getPort(), fromAddress, adminEmail);
            return true;
        } catch (org.springframework.mail.MailAuthenticationException e) {
            log.error("[配置] 测试邮件认证失败: host={}, username={}, err={}",
                    config.getHost(), config.getUsername(), e.getMessage());
            throw new BusinessException(ResultCode.FAIL,
                    "SMTP 认证失败：请检查授权码是否正确。注意 QQ/163 邮箱需使用授权码，非登录密码。");
        } catch (org.springframework.mail.MailSendException e) {
            String msg = e.getMessage();
            log.error("[配置] 测试邮件发送失败: host={}, port={}, err={}",
                    config.getHost(), config.getPort(), msg);
            if (msg != null && msg.contains("550")) {
                throw new BusinessException(ResultCode.FAIL,
                        "发件人被拒绝(550)：请确认邮箱已开启 SMTP 服务，且发件地址与登录账号一致。");
            }
            if (msg != null && msg.contains("535")) {
                throw new BusinessException(ResultCode.FAIL,
                        "认证失败(535)：授权码错误，请前往邮箱后台重新生成 SMTP 授权码。");
            }
            throw new BusinessException(ResultCode.FAIL, "测试邮件发送失败: " + msg);
        } catch (Exception e) {
            log.error("[配置] 测试邮件发送异常: host={}, port={}, err={}",
                    config.getHost(), config.getPort(), e.getMessage());
            throw new BusinessException(ResultCode.FAIL, "测试邮件发送失败: " + e.getMessage());
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 权限检查：仅 SUPER_ADMIN
     */
    private void requireSuperAdmin() {
        LoginUser user = SecurityUtils.getCurrentUser();
        if (!ROLE_SUPER_ADMIN.equals(user.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅超级管理员可操作");
        }
    }

    /**
     * 权限检查：ADMIN 及以上
     */
    private void requireAdminOrAbove() {
        LoginUser user = SecurityUtils.getCurrentUser();
        if (!ROLE_SUPER_ADMIN.equals(user.getRole()) && !ROLE_ADMIN.equals(user.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅管理员可查看");
        }
    }

    /**
     * 冷却期检查（5分钟内同一配置项仅可修改一次）
     */
    private void checkCooldown(String type, String name) {
        String key = COOLDOWN_PREFIX + type + ":" + name;
        Boolean exists = stringRedisTemplate.hasKey(key);
        if (Boolean.TRUE.equals(exists)) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, "该配置5分钟内已修改过，请稍后再试");
        }
    }

    /**
     * 设置冷却期
     */
    private void setCooldown(String type, String name) {
        String key = COOLDOWN_PREFIX + type + ":" + name;
        stringRedisTemplate.opsForValue().set(key, "1", COOLDOWN_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 限流器参数范围校验
     * limitForPeriod: 1-10000
     * limitRefreshPeriod: 1s-1h
     * timeoutDuration: 0s-60s
     */
    private void validateRateLimiterConfig(RateLimiterConfigVO config) {
        if (config.getLimitForPeriod() == null || config.getLimitForPeriod() < 1 || config.getLimitForPeriod() > 10000) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "limitForPeriod 范围: 1-10000");
        }
        Duration refreshPeriod = parseDuration(config.getLimitRefreshPeriod());
        if (refreshPeriod == null || refreshPeriod.getSeconds() < 1 || refreshPeriod.getSeconds() > 3600) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "limitRefreshPeriod 范围: 1s-1h");
        }
        Duration timeout = parseDuration(config.getTimeoutDuration());
        if (timeout == null || timeout.getSeconds() < 0 || timeout.getSeconds() > 60) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "timeoutDuration 范围: 0s-60s");
        }
    }

    /**
     * 熔断器参数范围校验
     * failureRateThreshold: 1-100
     * slowCallRateThreshold: 1-100
     * waitDurationInOpenState: 1s-10min
     */
    private void validateCircuitBreakerConfig(CircuitBreakerConfigVO config) {
        if (config.getFailureRateThreshold() == null || config.getFailureRateThreshold() < 1 || config.getFailureRateThreshold() > 100) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "failureRateThreshold 范围: 1-100");
        }
        if (config.getSlowCallRateThreshold() == null || config.getSlowCallRateThreshold() < 1 || config.getSlowCallRateThreshold() > 100) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "slowCallRateThreshold 范围: 1-100");
        }
        Duration waitDuration = parseDuration(config.getWaitDurationInOpenState());
        if (waitDuration == null || waitDuration.getSeconds() < 1 || waitDuration.getSeconds() > 600) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "waitDurationInOpenState 范围: 1s-10min");
        }
        if (config.getPermittedNumberOfCallsInHalfOpenState() == null || config.getPermittedNumberOfCallsInHalfOpenState() < 1) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "permittedNumberOfCallsInHalfOpenState 必须 >= 1");
        }
    }

    /**
     * 将限流器配置应用到Resilience4j注册表（实时生效）
     */
    private void applyRateLimiterConfig(String name, RateLimiterConfigVO config) {
        try {
            RateLimiterConfig newConfig = RateLimiterConfig.custom()
                    .limitForPeriod(config.getLimitForPeriod())
                    .limitRefreshPeriod(parseDuration(config.getLimitRefreshPeriod()))
                    .timeoutDuration(parseDuration(config.getTimeoutDuration()))
                    .build();
            // Resilience4j 2.x 不支持 changeConfig，需要移除旧实例并重新注册
            rateLimiterRegistry.remove(name);
            rateLimiterRegistry.rateLimiter(name, newConfig);
            log.info("[配置] 限流器配置已应用到注册表: name={}", name);
        } catch (Exception e) {
            log.error("[配置] 限流器配置应用失败: name={}, err={}", name, e.getMessage());
        }
    }

    /**
     * 将熔断器配置应用到Resilience4j注册表（实时生效）
     * 使用 from() 保留原有未修改的参数（如 slidingWindowSize, ignoreExceptions 等）
     */
    private void applyCircuitBreakerConfig(String name, CircuitBreakerConfigVO config) {
        try {
            CircuitBreaker existing = circuitBreakerRegistry.find(name).orElse(null);
            if (existing == null) {
                log.warn("[配置] 熔断器不存在，跳过应用: name={}", name);
                return;
            }
            CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.from(existing.getCircuitBreakerConfig());
            builder.failureRateThreshold(config.getFailureRateThreshold().floatValue());
            builder.slowCallRateThreshold(config.getSlowCallRateThreshold().floatValue());
            builder.slowCallDurationThreshold(parseDuration(config.getSlowCallDurationThreshold()));
            builder.waitDurationInOpenState(parseDuration(config.getWaitDurationInOpenState()));
            builder.permittedNumberOfCallsInHalfOpenState(config.getPermittedNumberOfCallsInHalfOpenState());

            // 移除旧的熔断器并注册新的（CircuitBreaker 不支持原地修改配置）
            circuitBreakerRegistry.remove(name);
            circuitBreakerRegistry.circuitBreaker(name, builder.build());
            log.info("[配置] 熔断器配置已应用到注册表: name={}", name);
        } catch (Exception e) {
            log.error("[配置] 熔断器配置应用失败: name={}, err={}", name, e.getMessage());
        }
    }

    /**
     * 获取限流器当前配置（用于操作日志记录修改前的值）
     */
    private RateLimiterConfigVO getRateLimiterCurrentConfig(String name) {
        RateLimiter rateLimiter = rateLimiterRegistry.find(name).orElse(null);
        if (rateLimiter != null) {
            RateLimiterConfigVO vo = new RateLimiterConfigVO();
            vo.setName(name);
            vo.setLimitForPeriod(rateLimiter.getRateLimiterConfig().getLimitForPeriod());
            vo.setLimitRefreshPeriod(durationToString(rateLimiter.getRateLimiterConfig().getLimitRefreshPeriod()));
            vo.setTimeoutDuration(durationToString(rateLimiter.getRateLimiterConfig().getTimeoutDuration()));
            return vo;
        }
        RateLimiterConfigVO empty = new RateLimiterConfigVO();
        empty.setName(name);
        empty.setLimitForPeriod(0);
        empty.setLimitRefreshPeriod("");
        empty.setTimeoutDuration("");
        return empty;
    }

    /**
     * 获取熔断器当前配置（用于操作日志记录修改前的值）
     */
    private CircuitBreakerConfigVO getCircuitBreakerCurrentConfig(String name) {
        CircuitBreaker cb = circuitBreakerRegistry.find(name).orElse(null);
        if (cb != null) {
            CircuitBreakerConfigVO vo = new CircuitBreakerConfigVO();
            vo.setName(name);
            vo.setFailureRateThreshold((double) cb.getCircuitBreakerConfig().getFailureRateThreshold());
            vo.setSlowCallRateThreshold((double) cb.getCircuitBreakerConfig().getSlowCallRateThreshold());
            vo.setSlowCallDurationThreshold(durationToString(cb.getCircuitBreakerConfig().getSlowCallDurationThreshold()));
            vo.setWaitDurationInOpenState(durationToString(Duration.ofMillis(cb.getCircuitBreakerConfig().getWaitIntervalFunctionInOpenState().apply(1))));
            vo.setPermittedNumberOfCallsInHalfOpenState(cb.getCircuitBreakerConfig().getPermittedNumberOfCallsInHalfOpenState());
            return vo;
        }
        CircuitBreakerConfigVO empty = new CircuitBreakerConfigVO();
        empty.setName(name);
        empty.setFailureRateThreshold(0.0);
        empty.setSlowCallRateThreshold(0.0);
        empty.setSlowCallDurationThreshold("");
        empty.setWaitDurationInOpenState("");
        empty.setPermittedNumberOfCallsInHalfOpenState(0);
        return empty;
    }

    /**
     * 从DB批量加载限流器覆盖配置
     */
    private Map<String, RateLimiterConfigVO> loadRateLimiterOverridesFromDb() {
        List<SystemConfig> configs = systemConfigMapper.selectList(
                new LambdaQueryWrapper<SystemConfig>()
                        .likeRight(SystemConfig::getConfigKey, CONFIG_KEY_RATE_LIMITER_PREFIX));
        Map<String, RateLimiterConfigVO> map = new HashMap<>();
        for (SystemConfig config : configs) {
            try {
                RateLimiterConfigVO vo = objectMapper.readValue(config.getConfigValue(), RateLimiterConfigVO.class);
                String name = config.getConfigKey().substring(CONFIG_KEY_RATE_LIMITER_PREFIX.length());
                map.put(name, vo);
            } catch (Exception e) {
                log.warn("[配置] 解析限流器DB配置失败: key={}", config.getConfigKey());
            }
        }
        return map;
    }

    /**
     * 从DB批量加载熔断器覆盖配置
     */
    private Map<String, CircuitBreakerConfigVO> loadCircuitBreakerOverridesFromDb() {
        List<SystemConfig> configs = systemConfigMapper.selectList(
                new LambdaQueryWrapper<SystemConfig>()
                        .likeRight(SystemConfig::getConfigKey, CONFIG_KEY_CIRCUIT_BREAKER_PREFIX));
        Map<String, CircuitBreakerConfigVO> map = new HashMap<>();
        for (SystemConfig config : configs) {
            try {
                CircuitBreakerConfigVO vo = objectMapper.readValue(config.getConfigValue(), CircuitBreakerConfigVO.class);
                String name = config.getConfigKey().substring(CONFIG_KEY_CIRCUIT_BREAKER_PREFIX.length());
                map.put(name, vo);
            } catch (Exception e) {
                log.warn("[配置] 解析熔断器DB配置失败: key={}", config.getConfigKey());
            }
        }
        return map;
    }

    /**
     * 从DB加载邮件配置（不脱敏，内部使用）
     */
    private EmailConfigVO loadEmailConfigFromDb() {
        SystemConfig config = systemConfigMapper.selectOne(
                new LambdaQueryWrapper<SystemConfig>()
                        .eq(SystemConfig::getConfigKey, CONFIG_KEY_EMAIL));
        if (config != null && config.getConfigValue() != null) {
            try {
                return objectMapper.readValue(config.getConfigValue(), EmailConfigVO.class);
            } catch (Exception e) {
                log.warn("[配置] 解析邮件配置JSON失败: {}", e.getMessage());
            }
        }
        return null;
    }

    /**
     * 系统配置 upsert(存在则更新，不存在则插入)
     */
    private void upsertSystemConfig(String configKey, String configValue, String description, String updatedBy) {
        SystemConfig existing = systemConfigMapper.selectOne(
                new LambdaQueryWrapper<SystemConfig>()
                        .eq(SystemConfig::getConfigKey, configKey));
        if (existing != null) {
            existing.setConfigValue(configValue);
            existing.setDescription(description);
            existing.setUpdatedBy(updatedBy);
            systemConfigMapper.updateById(existing);
        } else {
            SystemConfig newConfig = new SystemConfig();
            newConfig.setConfigKey(configKey);
            newConfig.setConfigValue(configValue);
            newConfig.setDescription(description);
            newConfig.setUpdatedBy(updatedBy);
            systemConfigMapper.insert(newConfig);
        }
    }

    /**
     * 将对象序列化为 JSON 字符串
     */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("[配置] JSON序列化失败: {}", e.getMessage());
            throw new RuntimeException("JSON序列化失败", e);
        }
    }

    /**
     * Duration 转换为可读字符串
     * 如: "0s", "1s", "1m", "1h"
     */
    private String durationToString(Duration duration) {
        if (duration == null) {
            return null;
        }
        long seconds = duration.getSeconds();
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            return (seconds / 60) + "m";
        } else {
            return (seconds / 3600) + "h";
        }
    }

    /**
     * 解析时长字符串为 Duration
     * 支持: "0", "0s", "1s", "1m", "1h"
     */
    private Duration parseDuration(String str) {
        if (str == null || str.isBlank()) {
            return Duration.ZERO;
        }
        str = str.trim();
        try {
            if (str.endsWith("ms")) {
                return Duration.ofMillis(Long.parseLong(str.substring(0, str.length() - 2)));
            }
            if (str.endsWith("s")) {
                return Duration.ofSeconds(Long.parseLong(str.substring(0, str.length() - 1)));
            }
            if (str.endsWith("m")) {
                return Duration.ofMinutes(Long.parseLong(str.substring(0, str.length() - 1)));
            }
            if (str.endsWith("h")) {
                return Duration.ofHours(Long.parseLong(str.substring(0, str.length() - 1)));
            }
            // 无后缀，默认秒
            return Duration.ofSeconds(Long.parseLong(str));
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "无效的时间格式: " + str);
        }
    }
}
