package com.chatvibe.module.user.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 用户在线状态枚举
 *
 * @author Alu
 * @date 2026-06-30
 */
@Getter
@AllArgsConstructor
public enum UserStatusEnum {
    /**
     * 状态: 0-离线 1-在线 2-忙碌 3-离开
     */
    OFFLINE(0, "离线"),
    ONLINE(1, "在线"),
    BUSY(2, "忙碌"),
    AWAY(3, "离开");
    private final Integer code;
    private final String message;

    /**
     * 根据状态码获取枚举，未匹配返回 null
     */
    public static UserStatusEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(e -> e.code.equals(code))
                .findFirst()
                .orElse(null);
    }

    /**
     * 校验状态码是否有效
     */
    public static boolean isValid(Integer code) {
        return fromCode(code) != null;
    }
}
