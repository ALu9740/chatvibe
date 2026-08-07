package com.chatvibe.module.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 删除消息 DTO
 *
 * @author Alu
 * @date 2026-08-07
 */
@Data
public class DeleteMessageDTO {

    /**
     * 删除原因
     */
    @NotBlank(message = "删除原因不能为空")
    private String reason;
}
