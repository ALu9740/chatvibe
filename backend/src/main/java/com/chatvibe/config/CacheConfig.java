package com.chatvibe.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
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
        cacheManager.setCacheNames(List.of("userInfo", "userEmail","emailExists","userNotifyPrefs"));
        cacheManager.registerCustomCache("userSearch",Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .maximumSize(200)
                .recordStats()
                .build());
        cacheManager.registerCustomCache("friendList", Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(500)
                .recordStats()
                .build());
        cacheManager.registerCustomCache("groupDetail", Caffeine.newBuilder()
                .expireAfterWrite(3, TimeUnit.MINUTES)
                .maximumSize(500)
                .recordStats()
                .build());
        cacheManager.registerCustomCache("groupMembers", Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(500)
                .recordStats()
                .build());
        cacheManager.registerCustomCache("notifUnreadCount", Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.SECONDS)
                .maximumSize(500)
                .recordStats()
                .build());
        return cacheManager;
    }

}
