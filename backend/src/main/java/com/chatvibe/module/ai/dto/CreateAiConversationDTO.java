package com.chatvibe.module.ai.dto;

import lombok.Data;

/**
 * 创建 AI 会话 DTO
 *
 * @author Alu
 * @date 2026-07-01
 */
@Data
public class CreateAiConversationDTO {

    /**
     * 会话标题(可选)
     */
    private String title;
}
