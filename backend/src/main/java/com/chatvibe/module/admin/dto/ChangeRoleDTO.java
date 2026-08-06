package com.chatvibe.module.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 修改用户角色 DTO
 *
 * @author Alu
 * @date 2026-08-06
 */
@Data
public class ChangeRoleDTO {

    /**
     * 角色: USER/ADMIN
     */
    @NotBlank(message = "角色不能为空")
    private String role;

    /**
     * 修改原因
     */
    private String reason;
}
