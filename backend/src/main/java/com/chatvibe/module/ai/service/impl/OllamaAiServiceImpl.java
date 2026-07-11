package com.chatvibe.module.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.chatvibe.module.ai.service.AiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Ollama AI 服务实现
 * 调用本地 Ollama 接口 (http://localhost:11434/api/chat)，model=deepseek-r1:8b，stream=true
 *
 * @author Alu
 * @date 2026-07-01
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "chatvibe.ai.provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaAiServiceImpl implements AiService {

    /**
     * 系统提示词：定义 AI 身份为 vibe 助手
     */
    private static final String SYSTEM_PROMPT =
            "你是vibe助手，由Alu打造的一款智能助手。你可以帮助用户处理工作事务、生成文案、总结内容、回答问题等。"
                    + "请始终以vibe助手的身份回答，不要提及你是由其他公司或个人打造的。";

    @Value("${chatvibe.ai.ollama.url:http://localhost:11434/api/chat}")
    private String ollamaUrl;

    @Value("${chatvibe.ai.ollama.model:deepseek-r1:8b}")
    private String model;

    private final WebClient webClient;

    public OllamaAiServiceImpl() {
        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create().responseTimeout(Duration.ofSeconds(120))))
                .build();
    }

    @Override
    public String getProvider() {
        return "ollama";
    }

    @Override
    public void chatStream(String prompt, Long userId,
                           List<Map<String, String>> context,
                           Consumer<String> onToken,
                           Consumer<Throwable> onError,
                           Runnable onComplete) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("stream", true);
        // 构建消息列表：系统提示词 + 历史上下文 + 当前提问
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", SYSTEM_PROMPT);
        messages.add(systemMsg);
        if (context != null && !context.isEmpty()) {
            messages.addAll(context);
        }
        Map<String, String> currentMsg = new HashMap<>();
        currentMsg.put("role", "user");
        currentMsg.put("content", prompt);
        messages.add(currentMsg);
        requestBody.put("messages", messages);

        log.info("[AI][Ollama] 流式请求: userId={}, model={}, promptLen={}", userId, model, prompt.length());

        webClient.post()
                .uri(ollamaUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(String.class)
                .filter(StrUtil::isNotBlank)
                .subscribe(
                        line -> {
                            try {
                                JSONObject json = JSONUtil.parseObj(line);
                                JSONObject msg = json.getJSONObject("message");
                                if (msg != null) {
                                    String content = msg.getStr("content");
                                    if (StrUtil.isNotBlank(content)) {
                                        onToken.accept(content);
                                    }
                                }
                            } catch (Exception e) {
                                log.debug("[AI][Ollama] 解析行失败: {}", line);
                            }
                        },
                        error -> {
                            log.error("[AI][Ollama] 流式请求异常: {}", error.getMessage());
                            onError.accept(error);
                        },
                        () -> {
                            log.info("[AI][Ollama] 流式请求完成: userId={}", userId);
                            onComplete.run();
                        }
                );
    }
}
