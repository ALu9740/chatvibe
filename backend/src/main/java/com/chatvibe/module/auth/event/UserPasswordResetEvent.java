package com.chatvibe.module.auth.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 密码重置事件
 *
 * @author Alu
 * @date 2026-07-15
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPasswordResetEvent implements Serializable {
    private Long userId;
    private String email;
    private long resetTime;
}
