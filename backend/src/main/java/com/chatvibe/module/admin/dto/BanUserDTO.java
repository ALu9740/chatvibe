package com.chatvibe.module.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 封禁用户 DTO
 *
 * @author Alu
 * @date 2026-08-06
 */
@Data
public class BanUserDTO {

    /**
     * 封禁类型: temp-临时封禁 permanent-永久封禁
     */
    @NotNull(message = "封禁类型不能为空")
    private String type;

    /**
     * 封禁时长(临时封禁时填写，如 1d、7d)
     */
    private String duration;

    /**
     * 封禁原因
     */
    @NotBlank(message = "封禁原因不能为空")
    private String reason;
}
