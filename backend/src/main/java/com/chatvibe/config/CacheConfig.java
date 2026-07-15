package com.chatvibe.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Cache 本地缓存配置
 *
 * @author Alu
 * @date 2026-07-14
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * 缓存名称与过期策略
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(500)
                .recordStats());
        cacheManager.setCacheNames(java.util.List.of("userInfo", "userEmail","emailExists"));
        return cacheManager;
    }

}
