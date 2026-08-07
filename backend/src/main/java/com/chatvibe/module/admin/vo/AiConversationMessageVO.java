package com.chatvibe.module.admin.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * AI 对话消息 VO（管理员只读视图）
 *
 * @author Alu
 * @date 2026-08-07
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiConversationMessageVO {

    /**
     * 消息ID
     */
    private Long id;

    /**
     * 发送者ID(0表示AI)
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
     * 消息类型: 0-文本 1-图片 2-语音 3-文件 4-系统
     */
    private Integer type;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 是否为AI消息
     */
    private Boolean isAi;

    /**
     * 发送时间(格式化字符串)
     */
    private String createdAt;
}
