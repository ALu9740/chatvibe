package com.chatvibe.module.user.vo;

import lombok.Data;

/**
 * 通知偏好 VO
 *
 * @author Alu
 * @date 2026-06-28
 */
@Data
public class NotificationPreferencesVO {

    /**
     * 桌面通知
     */
    private Boolean desktop;

    /**
     * 声音通知
     */
    private Boolean sound;

    /**
     * AI 消息提醒
     */
    private Boolean aiAlert;
}
