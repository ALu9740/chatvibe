package com.chatvibe.module.friend.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 好友请求事件（发送到 RabbitMQ 异步处理通知）
 *
 * @author Alu
 * @date 2026-07-20
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FriendRequestEvent implements Serializable {
    private Long requestId;
    private Long fromUid;
    private Long toUid;
    private String fromNickname;
    private String message;
}