package com.chatvibe.module.chat.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.chatvibe.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 会话成员实体
 *
 * @author Alu
 * @date 2026-06-27
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("conversation_member")
public class ConversationMember extends BaseEntity {

    /**
     * 角色: 0-成员 1-管理员 2-群主
     */
/*    public static final int ROLE_MEMBER = 0;
    public static final int ROLE_ADMIN = 1;
    public static final int ROLE_OWNER = 2;*/

    /**
     * 会话ID
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
     * 最后已读时间
     */
    private LocalDateTime lastReadAt;

    /**
     * 未读消息数
     */
    private Integer unreadCount;

    /**
     * 是否免打扰: 0-否 1-是
     */
    private Integer muted;

    /**
     * 是否置顶: 0-否 1-是
     */
    private Integer pinned;
}
