package com.chatvibe.module.notification.service;

import com.chatvibe.module.notification.enums.NotificationTypeEnum;
import com.chatvibe.module.notification.vo.NotificationVO;

import java.util.List;

/**
 * 通知服务接口
 *
 * @author Alu
 * @date 2026-07-02
 */
public interface NotificationService {

    /**
     * 创建通知并 WebSocket 推送
     *
     * @param userId  接收通知的用户ID
     * @param type    通知类型
     * @param title   通知标题
     * @param content 通知内容
     * @param extra   附加数据JSON
     */
    void createNotification(Long userId, NotificationTypeEnum type, String title, String content, String extra);

    /**
     * 当前用户通知列表
     *
     * @return 通知列表
     */
    List<NotificationVO> getNotificationList();

    /**
     * 当前用户未读数
     *
     * @return 未读通知数
     */
    Integer getUnreadCount();

    /**
     * 标记指定通知为已读
     *
     * @param notificationId 通知ID
     */
    void markAsRead(Long notificationId);

    /**
     * 全部标记为已读
     */
    void markAllAsRead();

    /**
     * 删除通知
     *
     * @param notificationId 通知ID
     */
    void deleteNotification(Long notificationId);
}
