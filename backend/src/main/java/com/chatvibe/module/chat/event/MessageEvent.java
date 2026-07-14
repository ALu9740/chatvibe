package com.chatvibe.module.chat.event;

import lombok.Data;

/**
 * 消息事件（通过RabbitMQ异步推送）
 *
 * @author Alu
 * @date 2026-07-14
 */
@Data
public class MessageEvent {
    /**
     * 消息 ID
     */
    private Long messageId;

    /**
     * 会话 ID
     */
    private Long conversationId;

    /**
     * 发送者 ID
     */
    private Long senderId;

    /**
     * 发送者昵称
     */
    private String senderName;

    /**
     * 发送者头像
     */
    private String senderAvatar;

    /**
     * 消息类型
     */
    private Integer type;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 附加信息
     */
    private String extra;

    /**
     * 消息状态
     */
    private Integer status;

    /**
     * 创建时间（毫秒时间戳）
     */
    private Long createdAt;
}
