package com.chatvibe.module.group.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 群转让事件（通过RabbitMQ异步推送）
 *
 * @author Alu
 * @date 2026-07-21
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupTransferEvent implements Serializable {
    private Long conversationId;
    private Long newOwnerId;
    private String groupName;
}