package com.chatvibe.module.admin.controller;

import com.chatvibe.common.result.Result;
import com.chatvibe.module.admin.service.AdminConfigService;
import com.chatvibe.module.admin.vo.CacheStatVO;
import com.chatvibe.module.admin.vo.CircuitBreakerConfigVO;
import com.chatvibe.module.admin.vo.EmailConfigVO;
import com.chatvibe.module.admin.vo.RateLimiterConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员系统配置接口
 * 管理限流器、熔断器、缓存和邮件配置
 *
 * @author Alu
 * @date 2026-08-09
 */
@RestController
@RequestMapping("/api/admin/config")
@RequiredArgsConstructor
public class AdminConfigController {

    private final AdminConfigService adminConfigService;

    /**
     * 获取所有限流器配置
     */
    @GetMapping("/rate-limiters")
    public Result<List<RateLimiterConfigVO>> getRateLimiters() {
        return Result.success(adminConfigService.getRateLimiters());
    }

    /**
     * 更新限流器配置
     *
     * @param name   限流器名称
     * @param config 限流器配置
     */
    @PutMapping("/rate-limiters/{name}")
    public Result<Boolean> updateRateLimiter(@PathVariable String name, @RequestBody RateLimiterConfigVO config) {
        return Result.success(adminConfigService.updateRateLimiter(name, config));
    }

    /**
     * 获取所有熔断器配置
     */
    @GetMapping("/circuit-breakers")
    public Result<List<CircuitBreakerConfigVO>> getCircuitBreakers() {
        return Result.success(adminConfigService.getCircuitBreakers());
    }

    /**
     * 更新熔断器配置
     *
     * @param name   熔断器名称
     * @param config 熔断器配置
     */
    @PutMapping("/circuit-breakers/{name}")
    public Result<Boolean> updateCircuitBreaker(@PathVariable String name, @RequestBody CircuitBreakerConfigVO config) {
        return Result.success(adminConfigService.updateCircuitBreaker(name, config));
    }

    /**
     * 获取缓存统计信息
     */
    @GetMapping("/caches")
    public Result<List<CacheStatVO>> getCacheStats() {
        return Result.success(adminConfigService.getCacheStats());
    }

    /**
     * 清除指定缓存
     *
     * @param name 缓存名称
     */
    @DeleteMapping("/caches/{name}")
    public Result<Boolean> clearCache(@PathVariable String name) {
        return Result.success(adminConfigService.clearCache(name));
    }

    /**
     * 获取邮件配置
     */
    @GetMapping("/email")
    public Result<EmailConfigVO> getEmailConfig() {
        return Result.success(adminConfigService.getEmailConfig());
    }

    /**
     * 更新邮件配置
     *
     * @param config 邮件配置
     */
    @PutMapping("/email")
    public Result<Boolean> updateEmailConfig(@RequestBody EmailConfigVO config) {
        return Result.success(adminConfigService.updateEmailConfig(config));
    }

    /**
     * 发送测试邮件验证连通性（使用前端传入的配置，无需先保存）
     *
     * @param config 邮件配置
     */
    @PostMapping("/email/test")
    public Result<Boolean> sendTestEmail(@RequestBody EmailConfigVO config) {
        return Result.success(adminConfigService.sendTestEmail(config));
    }
}
