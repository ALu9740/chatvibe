package com.chatvibe.module.ai.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.chatvibe.module.ai.service.AiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

/**
 * AI 兜底服务实现
 * <p>
 * 当主服务（Spring AI ChatClient）不可用或熔断开启时返回兜底回复。
 * 始终注册；主服务由 {@code SpringAiChatService} 提供。
 * <p>
 * 迁移后返回 {@link Flux}&lt;String&gt;，逐字符模拟流式输出。
 *
 * @author Alu
 * @date 2026-07-01
 */
@Slf4j
@Service
public class AiFallbackServiceImpl implements AiService {

    private static final String[] FALLBACK_REPLIES = {
            "抱歉，AI 服务暂时不可用，请稍后再试。",
            "我当前无法连接到 AI 服务，请检查网络或稍后重试。",
            "AI 服务正在维护中，暂时无法响应您的请求。",
            "收到您的消息，但 AI 服务当前不可用。您可以稍后再试。"
    };

    @Override
    public String getProvider() {
        return "fallback";
    }

    @Override
    public Flux<String> chatStream(String prompt, List<org.springframework.ai.chat.messages.Message> context) {
        log.warn("[AI][Fallback] 使用兜底回复: promptLen={}", prompt == null ? 0 : prompt.length());
        String reply = FALLBACK_REPLIES[RandomUtil.randomInt(FALLBACK_REPLIES.length)];
        // 逐字符模拟流式输出，每 30ms 推送一个字符
        return Flux.fromStream(reply.chars().mapToObj(c -> String.valueOf((char) c)))
                .delayElements(Duration.ofMillis(30));
    }
}
