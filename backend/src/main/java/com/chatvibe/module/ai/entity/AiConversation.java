package com.chatvibe.module.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.chatvibe.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 会话实体
 *
 * @author Alu
 * @date 2026-07-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_conversation")
public class AiConversation extends BaseEntity {

    /**
     * 用户ID
     */
    private Long userId;

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
     * 上下文(JSON)
     */
    private String context;

    /**
     * 最后一次提问
     */
    private String lastPrompt;
}
