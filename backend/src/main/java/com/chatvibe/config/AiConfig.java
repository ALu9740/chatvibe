package com.chatvibe.config;

import com.chatvibe.module.ai.service.AiService;
import com.chatvibe.module.ai.service.impl.AiFallbackServiceImpl;
import com.chatvibe.module.ai.service.impl.OllamaAiServiceImpl;
import com.chatvibe.module.ai.service.impl.OpenAiServiceImpl;
import com.chatvibe.module.ai.service.impl.QwenAiServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * AI 服务配置
 * 根据 chatvibe.ai.provider 配置选择主 AI 服务 Bean
 * 支持: ollama（本地）| openai | qwen（阿里云百炼 DashScope）
 * 当主服务不可用时回退到 AiFallbackServiceImpl
 *
 * @author Alu
 * @date 2026-06-27
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AiConfig {

    @Value("${chatvibe.ai.provider:qwen}")
    private String provider;

    /**
     * 主 AI 服务
     * 优先选择与 provider 匹配的实现，不可用时回退到兜底服务
     */
    @Bean
    @Primary
    public AiService primaryAiService(ObjectProvider<OllamaAiServiceImpl> ollamaProvider,
                                      ObjectProvider<OpenAiServiceImpl> openaiProvider,
                                      ObjectProvider<QwenAiServiceImpl> qwenProvider,
                                      ObjectProvider<AiFallbackServiceImpl> fallbackProvider) {
        AiFallbackServiceImpl fallback = fallbackProvider.getIfAvailable(AiFallbackServiceImpl::new);
        AiService primary = switch (provider.toLowerCase()) {
            case "openai" -> {
                OpenAiServiceImpl openai = openaiProvider.getIfAvailable();
                yield openai != null ? openai : fallback;
            }
            case "ollama" -> {
                OllamaAiServiceImpl ollama = ollamaProvider.getIfAvailable();
                yield ollama != null ? ollama : fallback;
            }
            case "qwen" -> {
                QwenAiServiceImpl qwen = qwenProvider.getIfAvailable();
                yield qwen != null ? qwen : fallback;
            }
            default -> fallback;
        };
        log.info("[AI] 当前 AI 提供商: {}, 主服务: {}", provider, primary.getClass().getSimpleName());
        return primary;
    }
}
