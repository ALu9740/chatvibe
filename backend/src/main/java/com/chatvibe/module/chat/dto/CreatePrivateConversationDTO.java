package com.chatvibe.module.chat.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建私聊会话 DTO
 *
 * @author Alu
 * @date 2026-06-28
 */
@Data
public class CreatePrivateConversationDTO {

    @NotNull(message = "目标用户ID不能为空")
    private Long targetUserId;
}
