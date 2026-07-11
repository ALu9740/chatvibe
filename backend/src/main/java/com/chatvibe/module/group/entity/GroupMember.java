package com.chatvibe.module.group.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.chatvibe.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 群组成员实体
 *
 * @author Alu
 * @date 2026-07-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("group_member")
public class GroupMember extends BaseEntity {

    /**
     * 角色: 0-成员 1-管理员 2-群主
     */
    public static final int ROLE_MEMBER = 0;
    public static final int ROLE_ADMIN = 1;
    public static final int ROLE_OWNER = 2;

    /**
     * 群组会话ID
     */
    private Long conversationId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 角色: 0-成员 1-管理员 2-群主
     */
    private Integer role;

    /**
     * 加入时间
     */
    private LocalDateTime joinTime;
}
