package com.chatvibe.module.notification.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 通知类型枚举
 *
 * @author Alu
 * @date 2026-07-02
 */
@Getter
@AllArgsConstructor
public enum NotificationTypeEnum {
    SYSTEM(1, "系统消息"),
    FRIEND_REQUEST(2, "好友请求"),
    FRIEND_ACCEPT(3, "好友接受"),
    FRIEND_DELETE(4, "好友删除"),
    GROUP_INVITE(5, "群邀请"),
    GROUP_REMOVE(6, "被移除群"),
    GROUP_DISSOLVE(7, "群解散"),
    GROUP_TRANSFER(8, "群转让");

    private final Integer code;
    private final String description;

    public static NotificationTypeEnum fromCode(Integer code) {
        for (NotificationTypeEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        return null;
    }
}
