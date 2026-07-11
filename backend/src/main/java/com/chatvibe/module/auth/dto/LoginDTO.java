package com.chatvibe.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录 DTO
 *
 * @author Alu
 * @date 2026-06-27
 */
@Data
public class LoginDTO {

    @NotBlank(message = "邮箱不能为空")
    @jakarta.validation.constraints.Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "密码不能为空")
    private String password;
}
