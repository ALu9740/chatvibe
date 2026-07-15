package com.chatvibe.module.auth.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 用户登出事件
 *
 * @author Alu
 * @date 2026-07-15
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserLogoutEvent implements Serializable {
    private Long userId;
    private long logoutTime;
}
