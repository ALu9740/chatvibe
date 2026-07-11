package com.chatvibe.module.chat.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 会话成员表免打扰枚举
 *
 * @author Alu
 * @date 2026-07-01
 */
@Getter
@AllArgsConstructor
public enum ConversationMemberMutedEnum {
    /**
     * 是否免打扰: 0-否 1-是
     */
    MUTE_NO(0, "否"),
    MUTE_YES(1, "是");
    private final Integer code;
    private final String message;
}
