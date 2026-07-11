package com.chatvibe.module.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chatvibe.common.exception.BusinessException;
import com.chatvibe.common.result.ResultCode;
import com.chatvibe.module.notification.entity.Notification;
import com.chatvibe.module.notification.enums.NotificationTypeEnum;
import com.chatvibe.module.notification.mapper.NotificationMapper;
import com.chatvibe.module.notification.service.NotificationService;
import com.chatvibe.module.notification.vo.NotificationVO;
import com.chatvibe.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 通知服务实现
 *
 * @author Alu
 * @date 2026-07-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void createNotification(Long userId, NotificationTypeEnum type, String title, String content, String extra) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type.getCode());
        notification.setTitle(title);
        notification.setContent(content);
        notification.setExtra(extra);
        notification.setIsRead(0);
        notificationMapper.insert(notification);
        NotificationVO vo = toVO(notification);
        try {
            messagingTemplate.convertAndSend("/topic/user." + userId + ".notification", vo);
        } catch (Exception e) {
            log.warn("[通知] WebSocket 推送失败: userId={}, type={}, err={}", userId, type, e.getMessage());
        }
    }

    @Override
    public List<NotificationVO> getNotificationList() {
        Long userId = SecurityUtils.getCurrentUserId();
        List<Notification> list = notificationMapper.selectList(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getCreatedAt));
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public Integer getUnreadCount() {
        Long userId = SecurityUtils.getCurrentUserId();
        Long count = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0));
        return count == null ? 0 : count.intValue();
    }

    @Override
    public void markAsRead(Long notificationId) {
        Long userId = SecurityUtils.getCurrentUserId();
        Notification notification = notificationMapper.selectById(notificationId);
        if (notification == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "通知不存在");
        }
        if (!notification.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作此通知");
        }
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getId, notificationId)
                .set(Notification::getIsRead, 1));
    }

    @Override
    public void markAllAsRead() {
        Long userId = SecurityUtils.getCurrentUserId();
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1));
    }

    @Override
    public void deleteNotification(Long notificationId) {
        Long userId = SecurityUtils.getCurrentUserId();
        Notification notification = notificationMapper.selectById(notificationId);
        if (notification == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "通知不存在");
        }
        if (!notification.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作此通知");
        }
        notificationMapper.deleteById(notificationId);
    }

    /**
     * 实体转 VO
     */
    private NotificationVO toVO(Notification notification) {
        NotificationVO vo = new NotificationVO();
        vo.setId(notification.getId());
        vo.setType(notification.getType());
        NotificationTypeEnum typeEnum = NotificationTypeEnum.fromCode(notification.getType());
        vo.setTypeDesc(typeEnum != null ? typeEnum.getDescription() : null);
        vo.setTitle(notification.getTitle());
        vo.setContent(notification.getContent());
        vo.setExtra(notification.getExtra());
        vo.setIsRead(notification.getIsRead());
        vo.setCreatedAt(notification.getCreatedAt());
        return vo;
    }
}
