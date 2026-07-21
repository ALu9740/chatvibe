package com.chatvibe.module.group.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 群邀请事件（通过RabbitMQ异步推送）
 *
 * @author Alu
 * @date 2026-07-21
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupInviteEvent implements Serializable {
    private Long conversationId;
    private Long ownerId;
    private String ownerNickname;
    private String groupName;
    private List<Long> memberIds; // 被邀请的成员ID列表(已排除群主)
}