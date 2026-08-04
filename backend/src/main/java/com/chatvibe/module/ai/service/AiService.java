package com.chatvibe.module.ai.service;

import org.springframework.ai.chat.messages.Message;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * AI 服务接口
 * <p>
 * 迁移到 Spring AI 后，流式输出以 {@link Flux}&lt;String&gt; 形式返回，
 * 由调用方桥接到 SseEmitter 或同步收集。上下文使用 Spring AI 的
 * {@link Message} 表达，替代原 {@code List<Map<String,String>>}。
 *
 * @author Alu
 * @date 2026-07-01
 */
public interface AiService {

    /**
     * 获取 AI 提供商标识
     *
     * @return provider 名称
     */
    String getProvider();

    /**
     * 流式对话（含多轮上下文）
     *
     * @param prompt  用户本次提问
     * @param context 上下文消息列表（历史 user/assistant 消息，不含当前 prompt）
     * @return 流式 token 的 Flux；订阅后逐 token 推送，正常结束触发 complete
     */
    Flux<String> chatStream(String prompt, List<Message> context);
}
