package com.chatvibe.module.ai.service;

import com.chatvibe.module.ai.service.impl.AiFallbackServiceImpl;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
/**
 * AI 服务熔断器包装
 * 当主 AI 服务连续失败时自动熔断，直接走兜底服务
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
    public void chatStreamWithCircuitBreaker(String prompt, Long userId,
                                             List<Map<String, String>> context,
                                             Consumer<String> onToken,
                                             Consumer<Throwable> onError,
                                             Runnable onComplete) {
        log.debug("[熔断器] 调用主 AI 服务: userId={}", userId);
        aiService.chatStream(prompt, userId, context, onToken, onError, onComplete);
    }

    /**
     * 熔断降级方法：直接走兜底服务
     * 参数列表必须与主方法一致，最后多一个 Throwable 参数
     */
    private void fallbackChatStream(String prompt, Long userId,
                                    List<Map<String, String>> context,
                                    Consumer<String> onToken,
                                    Consumer<Throwable> onError,
                                    Runnable onComplete,
                                    Throwable throwable) {
        log.warn("[熔断器] AI 服务熔断，走兜底: userId={}, reason={}", userId, throwable.getMessage());
        aiFallbackService.chatStream(prompt, userId, context, onToken, onError, onComplete);
    }
}
