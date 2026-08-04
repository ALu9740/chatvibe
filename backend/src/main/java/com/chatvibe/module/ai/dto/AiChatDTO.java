package com.chatvibe.module.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AI 对话 DTO
 *
 * @author Alu
 * @date 2026-07-01
 */
@Data
public class AiChatDTO {

    /**
     * 用户提问内容
     */
    @NotBlank(message = "提问内容不能为空")
    private String prompt;

    /**
     * AI 会话ID
     */
    private Long conversationId;

    /**
     * 聊天会话ID(常规会话: 私聊/群聊/独立AI会话)
     * 用于将 AI 回复落库到该会话并通过 WebSocket 广播给所有成员。
     */
    private Long chatConversationId;
}
