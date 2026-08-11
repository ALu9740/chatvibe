package com.chatvibe.module.admin.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理员信息
 *
 * @author Alu
 * @date 2026-08-11
 */
@Data
public class AdminUserVO implements Serializable {

    /**
     * 用户ID
     */
    private Long id;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像
     */
    private String avatar;

    /**
     * 角色
     */
    private String role;

    /**
     * 最后登录时间
     */
    private String lastLoginAt;

    /**
     * 最后登录IP
     */
    private String lastLoginIp;
}
