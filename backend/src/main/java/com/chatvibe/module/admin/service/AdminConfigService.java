package com.chatvibe.module.admin.service;

import com.chatvibe.module.admin.vo.CacheStatVO;
import com.chatvibe.module.admin.vo.CircuitBreakerConfigVO;
import com.chatvibe.module.admin.vo.EmailConfigVO;
import com.chatvibe.module.admin.vo.RateLimiterConfigVO;

import java.util.List;

/**
 * 管理员系统配置服务接口
 *
 * @author Alu
 * @date 2026-08-09
 */
public interface AdminConfigService {

    /**
     * 获取所有限流器配置
     *
     * @return 限流器配置列表
     */
    List<RateLimiterConfigVO> getRateLimiters();

    /**
     * 更新限流器配置(存储覆盖配置到数据库)
     *
     * @param name   限流器名称
     * @param config 限流器配置
     * @return 是否更新成功
     */
    boolean updateRateLimiter(String name, RateLimiterConfigVO config);

    /**
     * 获取所有熔断器配置
     *
     * @return 熔断器配置列表
     */
    List<CircuitBreakerConfigVO> getCircuitBreakers();

    /**
     * 更新熔断器配置(存储覆盖配置到数据库)
     *
     * @param name   熔断器名称
     * @param config 熔断器配置
     * @return 是否更新成功
     */
    boolean updateCircuitBreaker(String name, CircuitBreakerConfigVO config);

    /**
     * 获取缓存统计信息
     *
     * @return 缓存统计列表
     */
    List<CacheStatVO> getCacheStats();

    /**
     * 清除指定缓存
     *
     * @param name 缓存名称
     * @return 是否清除成功
     */
    boolean clearCache(String name);

    /**
     * 获取邮件配置
     *
     * @return 邮件配置
     */
    EmailConfigVO getEmailConfig();

    /**
     * 更新邮件配置
     *
     * @param config 邮件配置
     * @return 是否更新成功
     */
    boolean updateEmailConfig(EmailConfigVO config);

    /**
     * 发送测试邮件验证连通性（使用前端传入的配置创建临时 sender，无需先保存）
     *
     * @param config 邮件配置（密码为 ****** 时自动从 DB 加载真实密码）
     * @return 是否发送成功
     */
    boolean sendTestEmail(EmailConfigVO config);
}
