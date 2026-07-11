package com.chatvibe.module.notification.controller;

import com.chatvibe.common.result.Result;
import com.chatvibe.module.notification.service.NotificationService;
import com.chatvibe.module.notification.vo.NotificationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 通知接口
 *
 * @author Alu
 * @date 2026-07-02
 */
@RestController
@RequestMapping("/api/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 通知列表
     */
    @GetMapping("/list")
    public Result<List<NotificationVO>> list() {
        return Result.success(notificationService.getNotificationList());
    }

    /**
     * 未读数
     */
    @GetMapping("/unread-count")
    public Result<Integer> unreadCount() {
        return Result.success(notificationService.getUnreadCount());
    }

    /**
     * 标记指定通知为已读
     */
    @PutMapping("/{id}/read")
    public Result<Void> markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return Result.success();
    }

    /**
     * 全部标记为已读
     */
    @PutMapping("/read-all")
    public Result<Void> markAllAsRead() {
        notificationService.markAllAsRead();
        return Result.success();
    }

    /**
     * 删除通知
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        notificationService.deleteNotification(id);
        return Result.success();
    }
}
