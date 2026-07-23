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
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
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

    private final CacheManager cacheManager;

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

        evictUnreadCountCache(userId);

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
    @RateLimiter(name = "notifListRateLimiter", fallbackMethod = "getNotificationListFallback")
    @CircuitBreaker(name = "notifListService", fallbackMethod = "getNotificationListFallback")
    public List<NotificationVO> getNotificationList() {
        Long userId = SecurityUtils.getCurrentUserId();
        List<Notification> list = notificationMapper.selectList(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getCreatedAt)
                .last("LIMIT 100"));
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    @RateLimiter(name = "notifUnreadRateLimiter", fallbackMethod = "getUnreadCountFallback")
    @CircuitBreaker(name = "notifUnreadService", fallbackMethod = "getUnreadCountFallback")
    public Integer getUnreadCount() {
        Long userId = SecurityUtils.getCurrentUserId();
        // Caffeine 缓存：未读数变更频率低，30s 缓存大幅减少 DB COUNT 查询
        Cache cache = cacheManager.getCache("notifUnreadCount");
        if (cache != null) {
            Integer cached = cache.get(userId, () -> {
                Long count = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, 0));
                return count == null ? 0 : count.intValue();
            });
            return cached;
        }
        Long count = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0));
        return count == null ? 0 : count.intValue();
    }

    @Override
    @RateLimiter(name = "notifReadRateLimiter", fallbackMethod = "markAsReadFallback")
    @CircuitBreaker(name = "notifReadService", fallbackMethod = "markAsReadFallback")
    @Transactional(rollbackFor = Exception.class)
    public void markAsRead(Long notificationId) {
        Long userId = SecurityUtils.getCurrentUserId();
        // 单条 UPDATE 带 userId 条件，合并校验+更新（SELECT + UPDATE → 1 条 UPDATE）
        int updated = notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getId, notificationId)
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1));
        if (updated == 0) {
            // 可能：通知不存在 / 无权操作 / 已是已读状态
            Notification existing = notificationMapper.selectById(notificationId);
            if (existing == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "通知不存在");
            }
            if (!existing.getUserId().equals(userId)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权操作此通知");
            }
            // 已是已读状态，静默成功
        }
        evictUnreadCountCache(userId);
    }

    @Override
    @RateLimiter(name = "notifReadRateLimiter", fallbackMethod = "markAllAsReadFallback")
    @CircuitBreaker(name = "notifReadService", fallbackMethod = "markAllAsReadFallback")
    @Transactional(rollbackFor = Exception.class)
    public void markAllAsRead() {
        Long userId = SecurityUtils.getCurrentUserId();
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1));
        evictUnreadCountCache(userId);
    }

    @Override
    @RateLimiter(name = "notifDeleteRateLimiter", fallbackMethod = "deleteNotificationFallback")
    @CircuitBreaker(name = "notifDeleteService", fallbackMethod = "deleteNotificationFallback")
    @Transactional(rollbackFor = Exception.class)
    public void deleteNotification(Long notificationId) {
        Long userId = SecurityUtils.getCurrentUserId();
        // 先查未读状态，用于判断是否需要清缓存
        Notification notification = notificationMapper.selectById(notificationId);
        if (notification == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "通知不存在");
        }
        if (!notification.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作此通知");
        }
        notificationMapper.deleteById(notificationId);
        // 仅删除未读通知时才清缓存（删除已读通知不影响未读数）
        if (notification.getIsRead() != null && notification.getIsRead() == 0) {
            evictUnreadCountCache(userId);
        }
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

    /** 清除指定用户的未读数缓存 */
    private void evictUnreadCountCache(Long userId) {
        Cache cache = cacheManager.getCache("notifUnreadCount");
        if (cache != null) {
            cache.evict(userId);
        }
    }

    /** 通知列表降级：限流 → 429；熔断 → 空列表 */
    private List<NotificationVO> getNotificationListFallback(Throwable t) {
        if (t instanceof RequestNotPermitted) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS);
        }
        log.warn("[通知] 列表熔断降级: err={}", t.getMessage());
        return Collections.emptyList();
    }

    /** 未读数降级：限流 → 429；熔断 → 0 */
    private Integer getUnreadCountFallback(Throwable t) {
        if (t instanceof RequestNotPermitted) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS);
        }
        log.warn("[通知] 未读数熔断降级: err={}", t.getMessage());
        return 0;
    }

    /** 标记已读降级：限流 → 429；BusinessException 透传；熔断 → 静默 */
    private void markAsReadFallback(Long notificationId, Throwable t) {
        if (t instanceof RequestNotPermitted) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS);
        }
        if (t instanceof BusinessException) {
            throw (BusinessException) t;
        }
        log.warn("[通知] 标记已读熔断降级: id={}, err={}", notificationId, t.getMessage());
    }

    /** 全部标记已读降级 */
    private void markAllAsReadFallback(Throwable t) {
        if (t instanceof RequestNotPermitted) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS);
        }
        log.warn("[通知] 全部已读熔断降级: err={}", t.getMessage());
    }

    /** 删除通知降级 */
    private void deleteNotificationFallback(Long notificationId, Throwable t) {
        if (t instanceof RequestNotPermitted) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS);
        }
        if (t instanceof BusinessException) {
            throw (BusinessException) t;
        }
        log.warn("[通知] 删除通知熔断降级: id={}, err={}", notificationId, t.getMessage());
    }
}
