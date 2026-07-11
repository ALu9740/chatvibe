package com.chatvibe.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 * 注册静态资源映射，用于提供本地上传文件访问
 * /uploads/** → file:./uploads/
 *
 * @author Alu
 * @date 2026-06-27
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${chatvibe.upload.url-prefix:/uploads}")
    private String urlPrefix;

    @Value("${chatvibe.upload.base-dir:./uploads}")
    private String baseDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String prefix = urlPrefix.endsWith("/") ? urlPrefix : urlPrefix + "/";
        String dir = baseDir.endsWith("/") ? baseDir : baseDir + "/";
        registry.addResourceHandler(prefix + "**")
                .addResourceLocations("file:" + dir);
    }
}
