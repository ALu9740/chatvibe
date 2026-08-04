package com.chatvibe.module.ai.service.impl;

import com.chatvibe.config.AiConfig.ProviderChatClient;
import com.chatvibe.module.ai.service.AiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * 多供应商故障转移 AI 服务实现
 * <p>
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
 * 当 {@code chatvibe.ai.failover.enabled=true} 时激活（默认启用）。
 * 注意：不能用 {@code @ConditionalOnBean(ProviderChatClient.class)}，
 * 因为 {@code ProviderChatClient} 是 record，注册的是 {@code List<ProviderChatClient>}
 * 整体 Bean，而非单个 {@code ProviderChatClient} Bean，条件永远不匹配。
 *
 * @author Alu
 * @date 2026-07-26
 */
@Slf4j
@Service
@Primary
@ConditionalOnProperty(prefix = "chatvibe.ai.failover", name = "enabled", havingValue = "true")
public class FailoverAiService implements AiService {

    private final List<ProviderChatClient> providers;
    private final AiFallbackServiceImpl fallbackService;

    @Value("${chatvibe.ai.provider:ollama}")
    private String provider;

    public FailoverAiService(
            List<ProviderChatClient> providers,
            AiFallbackServiceImpl fallbackService) {
        this.providers = providers;
        this.fallbackService = fallbackService;
    }

    @Override
    public String getProvider() {
        return provider + "-failover";
    }

    @Override
    public Flux<String> chatStream(String prompt, List<Message> context) {
        if (providers == null || providers.isEmpty()) {
            log.warn("[AI][Failover] 无可用供应商，走兜底");
            return fallbackService.chatStream(prompt, context);
        }

        log.info("[AI][Failover] 开始故障转移调用: providerCount={}, promptLen={}",
                providers.size(), prompt == null ? 0 : prompt.length());

        // 从第一个供应商开始尝试
        return tryProvider(0, prompt, context, false);
    }

    /**
     * 递归尝试第 index 个供应商
     *
     * @param index         当前供应商索引
     * @param prompt        用户提问
     * @param context       上下文消息
     * @param hasEmitted    是否已有供应商发出过 token（用于判断是否可切换）
     * @return Flux<String> 流式 token
     */
    private Flux<String> tryProvider(int index, String prompt, List<Message> context, boolean hasEmitted) {
        // 所有供应商都失败 → 走兜底
        if (index >= providers.size()) {
            log.warn("[AI][Failover] 所有供应商均失败，走固定文本兜底");
            return fallbackService.chatStream(prompt, context);
        }

        ProviderChatClient providerClient = providers.get(index);
        List<Message> messages = new ArrayList<>();
        if (context != null && !context.isEmpty()) {
            messages.addAll(context);
        }

        final boolean emitted = hasEmitted;

        return providerClient.chatClient()
                .prompt()
                .messages(messages)
                .user(prompt)
                .stream()
                .content()
                // 首个 token 发出后标记为已发射，防止后续出错时切换供应商
                .doOnNext(token -> {
                    if (!emitted) {
                        log.info("[AI][Failover] 供应商 {} 响应成功，开始输出", providerClient.name());
                    }
                })
                .onErrorResume(error -> {
                    // 如果已有 token 输出，不切换（避免内容混乱）
                    if (emitted) {
                        log.warn("[AI][Failover] 供应商 {} 流式中途异常（已有输出，不切换）: {}",
                                providerClient.name(), error.getMessage());
                        return Flux.error(error);
                    }

                    // 首次调用即失败 → 切换到下一个供应商
                    log.warn("[AI][Failover] 供应商 {} 调用失败，切换到下一个: {}",
                            providerClient.name(), error.getMessage());
                    return tryProvider(index + 1, prompt, context, false);
                });
    }
}
