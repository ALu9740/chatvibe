package com.chatvibe.module.ai.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.chatvibe.module.ai.service.AiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * AI 兜底服务实现
 * 当主服务(Ollama/OpenAI)不可用时返回兜底回复
 * 始终注册，由 AiConfig 的 @Primary 主服务区分注入
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
    public void chatStream(String prompt, Long userId,
                           List<Map<String, String>> context,
                           Consumer<String> onToken,
                           Consumer<Throwable> onError,
                           Runnable onComplete) {
        log.warn("[AI][Fallback] 使用兜底回复: userId={}, promptLen={}", userId, prompt.length());
        String reply = FALLBACK_REPLIES[RandomUtil.randomInt(FALLBACK_REPLIES.length)];
        // 逐字符模拟流式输出
        Thread simulator = new Thread(() -> {
            try {
                for (char c : reply.toCharArray()) {
                    onToken.accept(String.valueOf(c));
                    Thread.sleep(30);
                }
                onComplete.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                onError.accept(e);
            }
        }, "ai-fallback-" + userId);
        simulator.setDaemon(true);
        simulator.start();
    }
}
