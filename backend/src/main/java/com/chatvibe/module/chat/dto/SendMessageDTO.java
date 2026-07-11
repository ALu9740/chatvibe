package com.chatvibe.module.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 发送消息 DTO
 *
 * @author Alu
 * @date 2026-06-28
 */
@Data
public class SendMessageDTO {

    /**
     * 会话ID
     */
    @NotNull(message = "会话ID不能为空")
    private Long conversationId;

    /**
     * 消息类型: 0-文本 1-图片 2-语音 3-文件
     */
    private Integer type = 0;

    /**
     * 消息内容
     */
    @NotBlank(message = "消息内容不能为空")
    private String content;

    /**
     * 附加信息(JSON)
     */
    private String extra;
}
