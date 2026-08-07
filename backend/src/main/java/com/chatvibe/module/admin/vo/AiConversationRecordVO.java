package com.chatvibe.module.admin.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * AI 会话记录 VO（管理员视图）
 *
 * @author Alu
 * @date 2026-08-06
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiConversationRecordVO {

    /**
     * AI会话ID
     */
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户昵称
     */
    private String userNickname;

    /**
     * 会话标题
     */
    private String title;

    /**
     * AI 提供商
     */
    private String provider;

    /**
     * 模型名
     */
    private String model;

    /**
     * 最后消息时间(格式化字符串)
     */
    private String lastMessageAt;

    /**
     * 消息数量
     */
    private Integer messageCount;
}
