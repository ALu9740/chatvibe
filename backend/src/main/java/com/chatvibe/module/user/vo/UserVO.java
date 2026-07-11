package com.chatvibe.module.user.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户视图对象
 *
 * @author Alu
 * @date 2026-06-27
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserVO {

    private Long id;
    private String email;
    private String nickname;
    private String avatar;
    private String bio;
    private Integer status;
    private String role;
    private LocalDateTime createdAt;
}
