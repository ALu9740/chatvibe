package com.chatvibe.module.friend.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 好友关系状态
 *
 * @author Alu
 * @date 2026-06-30
 */
@Getter
@AllArgsConstructor
public enum FriedStatusEnum {
    /**
     * 状态: 0-待处理 1-已接受 2-已拒绝
     */
    STATUS_PENDING(0, "待处理"),
    STATUS_ACCEPTED(1, "已接受"),
    STATUS_REJECTED(2, "已拒绝");
    private final Integer code;
    private final String message;
}
