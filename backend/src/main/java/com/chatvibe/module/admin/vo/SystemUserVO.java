package com.chatvibe.module.admin.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 系统用户视图对象（管理员视角）
 *
 * @author Alu
 * @date 2026-08-06
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SystemUserVO {

    private Long id;

    private String email;

    private String nickname;

    private String avatar;

    /**
     * 状态: normal-正常 banned-已封禁
     */
    private String status;

    /**
     * 角色: USER/OPERATOR/ADMIN/SUPER_ADMIN
     */
    private String role;

    /**
     * 注册时间(yyyy-MM-dd HH:mm:ss)
     */
    private String createdAt;

    /**
     * 最后活跃时间(yyyy-MM-dd HH:mm:ss)
     */
    private String lastActiveAt;

    /**
     * 在线状态: 0-离线 1-在线 2-忙碌 3-离开
     */
    private Integer onlineStatus;

    private String bio;
}
