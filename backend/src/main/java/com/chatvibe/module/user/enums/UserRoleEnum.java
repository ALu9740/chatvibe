package com.chatvibe.module.user.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户角色枚举
 * <p>
 * 角色层级（从高到低）:
 * <ul>
 *   <li>SUPER_ADMIN - 超级管理员，拥有全部权限，可管理其他管理员</li>
 *   <li>ADMIN       - 管理员，拥有后台全部功能权限</li>
 *   <li>OPERATOR    - 运营员，可管理用户/消息/群组，不可修改系统配置</li>
 *   <li>USER        - 普通用户，无后台访问权限</li>
 * </ul>
 *
 * @author Alu
 * @date 2026-06-30
 */
@Getter
@AllArgsConstructor
public enum UserRoleEnum {
    USER("USER", "普通用户"),
    OPERATOR("OPERATOR", "运营员"),
    ADMIN("ADMIN", "管理员"),
    SUPER_ADMIN("SUPER_ADMIN", "超级管理员");
    private final String code;
    private final String message;

    /**
     * 判断给定角色是否为管理角色（可访问后台）
     */
    public static boolean isAdminRole(String role) {
        return OPERATOR.code.equals(role) || ADMIN.code.equals(role) || SUPER_ADMIN.code.equals(role);
    }
}
