package com.chatvibe.module.ai.service.impl;

import com.chatvibe.module.ai.service.AiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于 Spring AI {@link ChatClient} 的单供应商 AI 服务实现。
 * <p>
 * 替代原 {@code OllamaAiServiceImpl}/{@code OpenAiServiceImpl}/{@code QwenAiServiceImpl}
 * 三个手写 WebClient 实现：Qwen/Ollama(OpenAI 兼容模式)/OpenAI 均复用同一
 * OpenAI 兼容客户端，仅 base-url / api-key / model 不同（见 application.yml）。
 * <p>
 * 流式输出通过 {@code ChatClient.prompt().stream().content()} 返回
 * {@link Flux}&lt;String&gt;，由调用方桥接到 SseEmitter。
 * <p>
 * 标记 {@code @Primary} + {@code @ConditionalOnMissingBean(FailoverAiService.class)}：
 * 当故障转移模式启用时（FailoverAiService 存在），此类不会被创建；
 * 仅在单供应商模式下作为主 AiService 注入。
 *
 * @author Alu
 * @date 2026-07-24
 */
@Slf4j
@Service
@Primary
@ConditionalOnMissingBean(FailoverAiService.class)
@RequiredArgsConstructor
public class SpringAiChatService implements AiService {

    private final ChatClient chatClient;

    @Value("${chatvibe.ai.provider:ollama}")
    private String provider;

    @Override
    public String getProvider() {
        return provider;
    }

    @Override
    public Flux<String> chatStream(String prompt, List<Message> context) {
        List<Message> messages = new ArrayList<>();
        if (context != null && !context.isEmpty()) {
            messages.addAll(context);
        }
        log.info("[AI][SpringAI] 流式请求: provider={}, promptLen={}, ctxSize={}",
                provider, prompt == null ? 0 : prompt.length(), messages.size());

        // 系统提示词已在 ChatClient.defaultSystem(...) 中统一配置（见 AiConfig）
        return chatClient.prompt()
                .messages(messages)
                .user(prompt)
                .stream()
                .content();
    }
}
