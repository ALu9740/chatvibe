package com.chatvibe.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 跨域配置
 *
 * @author Alu
 * @date 2026-06-27
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // TODO:允许所有来源(生产环境应配置具体域名)
        config.setAllowedOriginPatterns(List.of("*"));
        // 允许的请求方法
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        // 允许的请求头
        config.setAllowedHeaders(List.of("*"));
        // 暴露的响应头
        config.setExposedHeaders(List.of("Authorization", "Content-Disposition"));
        // 允许携带凭证
        config.setAllowCredentials(true);
        // 预检请求缓存时间(秒)
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
