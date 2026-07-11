package com.chatvibe.module.chat.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话视图对象
 *
 * @author Alu
 * @date 2026-06-28
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConversationVO {

    private Long id;
    private String name;
    private Integer type;
    private String avatar;
    private Long ownerId;
    private String lastMessage;
    private LocalDateTime lastMessageAt;
    /** 最后一条消息类型: 0-文本 1-图片 3-文件 4-系统 */
    private Integer lastMessageType;
    /**
     * 会话成员数量(仅群聊有效)
     */
    private Integer memberCount;
    /**
     * 会话未读消息数量
     */
    private Integer unreadCount;
    /**
     * 最后一次阅读时间
     */
    private LocalDateTime lastReadAt;
    /**
     * 是否免打扰: 0-否 1-是
     */
    private Integer muted;
    /**
     * 是否置顶: 0-否 1-是
     */
    private Integer pinned;
    /**
     * 群组是否已解散: 0-否 1-是
     */
    private Integer dissolved;
    private LocalDateTime createdAt;
    /**
     * 私聊会话对方用户ID(仅 type=1 私聊时返回,用于前端定位好友)
     */
    private Long peerId;
    /**
     * 私聊会话对方在线状态(仅 type=1 私聊时返回)
     * 0-离线 1-在线 2-忙碌 3-离开
     */
    private Integer peerStatus;
    /**
     * 当前用户在该会话中的角色(来自 conversation_member.role)
     * 0-成员 1-管理员 2-群主
     */
    private Integer myRole;
}
