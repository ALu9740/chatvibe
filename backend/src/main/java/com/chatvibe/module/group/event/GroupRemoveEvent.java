package com.chatvibe.module.group.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 群移除成员事件（通过RabbitMQ异步推送）
 *
 * @author Alu
 * @date 2026-07-21
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupRemoveEvent implements Serializable {
    private Long conversationId;
    private Long removedUserId;
    private String groupName;
}