package com.chatvibe.module.user.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新用户资料 DTO
 *
 * @author Alu
 * @date 2026-06-28
 */
@Data
public class UpdateProfileDTO {

    @Size(max = 20, message = "昵称长度不能超过20")
    private String nickname;

    @Size(max = 255, message = "头像URL长度不能超过255")
    private String avatar;

    @Size(max = 50, message = "个人简介长度不能超过50")
    private String bio;
}
