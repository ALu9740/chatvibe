package com.chatvibe.module.group.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 群成员视图对象（包含用户信息 + 群内角色）
 *
 * @author Alu
 * @date 2026-06-28
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GroupMemberVO {

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
     * 群内角色: 0-成员 1-管理员 2-群主
     */
    private Integer role;

    /**
     * 加入时间
     */
    private LocalDateTime joinTime;
}
