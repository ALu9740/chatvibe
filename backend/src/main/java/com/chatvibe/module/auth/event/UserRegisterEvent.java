package com.chatvibe.module.auth.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户注册事件（发送到 RabbitMQ 异步处理）
 *
 * @author Alu
 * @date 2026-07-15
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisterEvent implements Serializable {
    private Long userId;
    private String email;
    private String nickname;
}
