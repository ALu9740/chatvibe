package com.chatvibe.module.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.chatvibe.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户实体
 *
 * @author Alu
 * @date 2026-06-27
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user")
public class User extends BaseEntity {

    /**
     * 邮箱(即登录账号)
     */
    private String email;

    /**
     * 密码(BCrypt 加密)
     */
    private String password;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像URL
     */
    private String avatar;

    /**
     * 个人简介
     */
    private String bio;

    /**
     * 在线状态: 0-离线 1-在线 2-忙碌 3-离开
     */
    private Integer status;

    /**
     * 角色: USER/ADMIN
     */
    private String role;

    /**
     * 桌面通知: 0-关闭 1-开启
     */
    private Integer notifyDesktop;

    /**
     * 声音通知: 0-关闭 1-开启
     */
    private Integer notifySound;

    /**
     * AI 消息提醒: 0-关闭 1-开启
     */
    private Integer notifyAiAlert;

    /**
     * 登录版本号：每次登录递增，旧 Token 携带的版本号不匹配时强制下线（多设备登录冲突处理）
     */
    private Integer loginVersion;
}
