package com.chatvibe.module.chat.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 会话类型枚举
 *
 * @author Alu
 * @date 2026-06-30
 */
@Getter
@AllArgsConstructor
public enum ConversationTypeEnum {
    /**
     * 会话类型: 1-私聊 2-群聊 3-AI
     */
    TYPE_PRIVATE(1, "私聊"),
    TYPE_GROUP(2, "群聊"),
    TYPE_AI(3, "AI");
    private final Integer code;
    private final String message;
}
