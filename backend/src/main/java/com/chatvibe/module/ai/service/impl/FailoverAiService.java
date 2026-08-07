package com.chatvibe.module.ai.service.impl;

import com.chatvibe.config.AiProviderRegistry;
import com.chatvibe.config.AiProviderRegistry.ProviderChatClient;
import com.chatvibe.module.ai.service.AiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * 多供应商故障转移 AI 服务实现
 * <p>
 * 从 {@link AiProviderRegistry} 动态获取数据库中配置的供应商列表，
 * 按优先级依次尝试每个供应商的 ChatClient，当前供应商流式调用失败时
 * 自动切换到下一个供应商，直到成功或全部失败。
 * <p>
 * 切换策略：
 * <ul>
 *   <li>Flux 发出 onError 信号 → 记录警告，尝试下一个供应商</li>
 *   <li>Flux 已发出至少一个 token 后出错 → 不切换（部分内容已推送，
 *       切换会导致内容拼接混乱），直接传递错误信号</li>
 *   <li>所有供应商都失败 → 走 {@link AiFallbackServiceImpl} 固定文本兜底</li>
 * </ul>
 * <p>
 * 配置完全由数据库驱动，管理员通过后台修改供应商配置后，
 * {@link AiProviderRegistry#refresh()} 会重建供应商列表，本服务实时生效。
 *
 * @author Alu
 * @date 2026-07-26
 */
@Slf4j
@Service
@Primary
public class FailoverAiService implements AiService {

    private final AiProviderRegistry aiProviderRegistry;
    private final AiFallbackServiceImpl fallbackService;

    public FailoverAiService(
            AiProviderRegistry aiProviderRegistry,
            AiFallbackServiceImpl fallbackService) {
        this.aiProviderRegistry = aiProviderRegistry;
        this.fallbackService = fallbackService;
    }

    @Override
    public String getProvider() {
        List<ProviderChatClient> providers = aiProviderRegistry.getProviders();
        if (providers == null || providers.isEmpty()) {
            return "none";
        }
        return providers.get(0).name();
    }

    @Override
    public Flux<String> chatStream(String prompt, List<org.springframework.ai.chat.messages.Message> context) {
        List<ProviderChatClient> providers = aiProviderRegistry.getProviders();
        if (providers == null || providers.isEmpty()) {
            log.warn("[AI][Failover] 无可用供应商，走兜底");
            return fallbackService.chatStream(prompt, context);
        }

        // 故障转移未启用时，仅使用第一个供应商
        if (!aiProviderRegistry.isFailoverEnabled()) {
            log.info("[AI][Failover] 故障转移未启用，仅使用主供应商: {}", providers.get(0).name());
            return callProvider(providers.get(0), prompt, context)
                    .onErrorResume(error -> {
                        log.warn("[AI][Failover] 主供应商失败(故障转移未启用)，走兜底: {}",
                                error.getMessage());
                        return fallbackService.chatStream(prompt, context);
                    });
        }

        log.info("[AI][Failover] 开始故障转移调用: providerCount={}, promptLen={}",
                providers.size(), prompt == null ? 0 : prompt.length());

        return tryProvider(0, providers, prompt, context, false);
    }

    /**
     * 递归尝试第 index 个供应商
     *
     * @param index      当前供应商索引
     * @param providers  供应商列表
     * @param prompt     用户提问
     * @param context    上下文消息
     * @param hasEmitted 是否已有供应商发出过 token（用于判断是否可切换）
     * @return Flux<String> 流式 token
     */
    private Flux<String> tryProvider(int index, List<ProviderChatClient> providers,
                                      String prompt, List<org.springframework.ai.chat.messages.Message> context,
                                      boolean hasEmitted) {
        if (index >= providers.size()) {
            log.warn("[AI][Failover] 所有供应商均失败，走固定文本兜底");
            return fallbackService.chatStream(prompt, context);
        }

        ProviderChatClient providerClient = providers.get(index);
        final boolean emitted = hasEmitted;

        return callProvider(providerClient, prompt, context)
                .doOnNext(token -> {
                    if (!emitted) {
                        log.info("[AI][Failover] 供应商 {} 响应成功，开始输出", providerClient.name());
                    }
                })
                .onErrorResume(error -> {
                    if (emitted) {
                        log.warn("[AI][Failover] 供应商 {} 流式中途异常（已有输出，不切换）: {}",
                                providerClient.name(), error.getMessage());
                        return Flux.error(error);
                    }
                    log.warn("[AI][Failover] 供应商 {} 调用失败，切换到下一个: {}",
                            providerClient.name(), error.getMessage());
                    return tryProvider(index + 1, providers, prompt, context, false);
                });
    }

    /**
     * 调用单个供应商的 ChatClient
     */
    private Flux<String> callProvider(ProviderChatClient providerClient,
                                       String prompt, List<org.springframework.ai.chat.messages.Message> context) {
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        if (context != null && !context.isEmpty()) {
            messages.addAll(context);
        }
        return providerClient.chatClient()
                .prompt()
                .messages(messages)
                .user(prompt)
                .stream()
                .content();
    }
}
