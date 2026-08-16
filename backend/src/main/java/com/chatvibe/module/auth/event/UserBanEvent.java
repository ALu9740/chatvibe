package com.chatvibe.module.auth.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户封禁/解封事件
 * <p>
 * 用于异步发送封禁/解封通知邮件，邮件内容包含操作原因、操作时间等信息。
 *
 * @author Alu
 * @date 2026-08-16
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserBanEvent implements Serializable {
    private Long userId;
    private String email;
    private String nickname;
    /** true=封禁, false=解封 */
    private boolean banned;
    /** 封禁原因（仅封禁时有值） */
    private String reason;
    /** 封禁类型: temp-临时 / permanent-永久（仅封禁时有值） */
    private String type;
    /** 封禁时长描述（仅封禁时有值，如 "7天" / "永久"） */
    private String duration;
    /** 操作时间戳 */
    private long operateTime;
}
