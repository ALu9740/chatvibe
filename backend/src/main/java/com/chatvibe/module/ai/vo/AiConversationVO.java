package com.chatvibe.module.ai.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 会话视图对象
 *
 * @author Alu
 * @date 2026-07-01
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiConversationVO {

    private Long id;
    private String title;
    private String provider;
    private String model;
    private String lastPrompt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
