package com.chatvibe.module.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 发送 AI 消息 DTO (非流式)
 *
 * @author Alu
 * @date 2026-07-01
 */
@Data
public class SendAiMessageDTO {

    @NotBlank(message = "消息内容不能为空")
    private String content;
}
