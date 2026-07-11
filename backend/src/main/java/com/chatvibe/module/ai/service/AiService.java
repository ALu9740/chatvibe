package com.chatvibe.module.ai.service;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * AI 服务接口
 * 支持流式输出(逐 token 返回)和多轮上下文
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
     * @param prompt     用户本次提问
     * @param userId     用户ID
     * @param context    上下文消息列表，每项为 {role, content}，role=user/assistant
     * @param onToken    每个 token 的回调
     * @param onError    异常回调
     * @param onComplete 完成回调
     */
    void chatStream(String prompt, Long userId,
                    List<Map<String, String>> context,
                    Consumer<String> onToken,
                    Consumer<Throwable> onError,
                    Runnable onComplete);
}
