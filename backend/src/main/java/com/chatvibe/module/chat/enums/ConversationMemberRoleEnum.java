package com.chatvibe.module.chat.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 会话成员角色枚举
 *
 * @author Alu
 * @date 2026-07-01
 */
@Getter
@AllArgsConstructor
public enum ConversationMemberRoleEnum {
    /**
     * 角色: 0-成员 1-管理员 2-群主
     */
    ROLE_MEMBER(0, "成员"),
    ROLE_ADMIN(1, "管理员"),
    ROLE_OWNER(2, "群主");
    private final Integer code;
    private final String description;
}
