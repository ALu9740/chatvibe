package com.chatvibe.module.group.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.util.List;

/**
 * 群解散事件（通过RabbitMQ异步推送）
 *
 * @author Alu
 * @date 2026-07-21
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupDissolveEvent implements Serializable {
    private Long conversationId;
    private String groupName;
    private List<Long> memberIds; // 需要通知的所有成员(排除群主)
}