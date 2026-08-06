package com.chatvibe.module.auth.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 密码重置事件
 * <p>
 * C端用户自主重置: newPassword 为 null，邮件仅通知"密码已重置"
 * 管理员重置: newPassword 携带新密码，邮件中展示新密码
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
    /** 新密码（仅管理员重置时携带，C端自主重置为 null） */
    private String newPassword;

    /** C端自主重置构造（不含新密码） */
    public UserPasswordResetEvent(Long userId, String email, long resetTime) {
        this.userId = userId;
        this.email = email;
        this.resetTime = resetTime;
        this.newPassword = null;
    }
}
