package com.chatvibe.module.admin.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 系统群组 VO（管理员视图）
 *
 * @author Alu
 * @date 2026-08-07
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SystemGroupVO {

    /**
     * 群组会话ID
     */
    private Long id;

    /**
     * 群名称
     */
    private String name;

    /**
     * 群头像
     */
    private String avatar;

    /**
     * 群主ID
     */
    private Long ownerId;

    /**
     * 群主昵称
     */
    private String ownerName;

    /**
     * 成员数
     */
    private Integer memberCount;

    /**
     * 状态: normal-正常 dissolved-已解散
     */
    private String status;

    /**
     * 创建时间(格式化字符串)
     */
    private String createdAt;

    /**
     * 最后消息时间(格式化字符串)
     */
    private String lastMessageAt;
}
