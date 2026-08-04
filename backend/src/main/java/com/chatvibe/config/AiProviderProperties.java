package com.chatvibe.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 多供应商故障转移配置属性
 * <p>
 * 绑定 application.yml 中 chatvibe.ai.failover 配置段。
 * providers 列表顺序即为故障转移优先级（索引 0 最高优先级）。
 *
 * @author Alu
 * @date 2026-07-26
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "chatvibe.ai.failover")
public class AiProviderProperties {

    /**
     * 是否启用故障转移（false 时退回单供应商模式）
     */
    private boolean enabled = true;

    /**
     * 供应商列表，按优先级排序
     */
    private List<ProviderConfig> providers = new ArrayList<>();

    @Data
    public static class ProviderConfig {
        /** 供应商标识：qwen / openai / ollama */
        private String name;
        /** API base-url */
        private String baseUrl;
        /** API 密钥（Ollama 可为 dummy） */
        private String apiKey;
        /** 模型名 */
        private String model;
        /** 是否启用此供应商（false 则跳过） */
        private boolean enabled = true;
    }
}
