package com.chatvibe.module.chat.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 会话成员置顶枚举
 *
 * @author Alu
 * @date 2026-07-01
 */
@Getter
@AllArgsConstructor
public enum ConversationMemberPinnedEnum {
    /**
     * 是否置顶: 0-否 1-是
     */
    PINNED_NO(0, "否"),
    PINNED_YES(1, "是");
    private final Integer code;
    private final String description;
}
