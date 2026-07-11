package com.chatvibe.module.user.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户角色枚举
 *
 * @author Alu
 * @date 2026-06-30
 */
@Getter
@AllArgsConstructor
public enum UserRoleEnum {
    /**
     * 角色: USER/ADMIN
     */
    USER("USER", "用户"),
    ADMIN("ADMIN", "管理员");
    private final String code;
    private final String message;
}
