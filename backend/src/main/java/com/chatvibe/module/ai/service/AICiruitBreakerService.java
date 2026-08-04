package com.chatvibe.module.ai.service;

import com.chatvibe.module.ai.service.impl.AiFallbackServiceImpl;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * AI 服务熔断器包装
 * <p>
 * 当主 AI 服务（Spring AI ChatClient）连续失败时自动熔断，直接走兜底服务。
 * 迁移后返回 {@link Flux}&lt;String&gt;，依赖 {@code resilience4j-reactor}
 * 支持 Reactor 类型上的 {@link CircuitBreaker} 注解。
 *
 * @author Alu
 * @date 2026-07-14
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AICiruitBreakerService {
    private final AiService aiService;
    private final AiFallbackServiceImpl aiFallbackService;

    /**
     * 带熔断的流式对话
     * 熔断器名称：aiService
     * 熔断时走 fallbackMethod → 直接调用兜底服务
     */
    @CircuitBreaker(name = "aiService", fallbackMethod = "fallbackChatStream")
    public Flux<String> chatStreamWithCircuitBreaker(String prompt, List<Message> context) {
        log.debug("[熔断器] 调用主 AI 服务: ctxSize={}", context == null ? 0 : context.size());
        return aiService.chatStream(prompt, context);
    }

    /**
     * 熔断降级方法：直接走兜底服务
     * 参数列表必须与主方法一致，最后多一个 Throwable 参数
     */
    private Flux<String> fallbackChatStream(String prompt, List<Message> context, Throwable throwable) {
        log.warn("[熔断器] AI 服务熔断，走兜底: reason={}", throwable.getMessage());
        return aiFallbackService.chatStream(prompt, context);
    }
}
