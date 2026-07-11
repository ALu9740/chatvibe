package com.chatvibe.websocket.dto;

import lombok.Data;

/**
 * WebSocket 消息 DTO
 *
 * @author Alu
 * @date 2026-06-27
 */
@Data
public class WsMessage {

    /**
     * 会话ID
     */
    private Long conversationId;

    /**
     * 发送者ID
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
     * 消息类型: 0-文本 1-图片 2-语音 3-文件
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
     * 时间戳
     */
    private Long timestamp;
}
