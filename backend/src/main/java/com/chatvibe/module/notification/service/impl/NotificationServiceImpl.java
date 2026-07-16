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
import com.chatvibe.module.user.service.UserService;
import com.chatvibe.module.user.vo.NotificationPreferencesVO;
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

    private final UserService userService;

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

        NotificationPreferencesVO prefs = userService.getNotificationPreferences(userId);

        if (isAiNotification(type) && !prefs.getAiAlert()) {
            log.info("[通知] 用户已关闭AI提醒，跳过推送: userId={}, type={}", userId, type);
            return;
        }

        if (!prefs.getDesktop()) {
            log.info("[通知] 用户已关闭桌面通知，跳过推送: userId={}, type={}", userId, type);
            return;
        }

        NotificationVO vo = toVO(notification);

        String extraWithSound = appendSoundFlag(extra, prefs.getSound());
        vo.setExtra(extraWithSound);

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

    /**
     * 判断是否为 AI 相关通知
     */
    private boolean isAiNotification(NotificationTypeEnum type) {
        // TODO:目前没有 AI 通知类型，预留扩展
        return false;
    }

    /**
     * 在 extra JSON 中追加声音标记
     */
    private String appendSoundFlag(String extra, Boolean soundEnabled) {
        boolean sound = soundEnabled != null && soundEnabled;
        if (extra == null || extra.isBlank()) {
            return "{\"silent\":" + !sound + "}";
        }
        if (extra.startsWith("{") && extra.endsWith("}")) {
            return extra.substring(0, extra.length() - 1) + ",\"silent\":" + !sound + "}";
        }
        return extra;
    }
}
