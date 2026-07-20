package com.chatvibe.module.friend.event;

import cn.hutool.json.JSONObject;
import com.chatvibe.config.RabbitMQConfig;
import com.chatvibe.module.notification.enums.NotificationTypeEnum;
import com.chatvibe.module.notification.service.NotificationService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 好友删除事件消费者：异步创建通知
 *
 * @author Alu
 * @date 2026-07-20
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FriendDeleteEventConsumer {
    private final NotificationService notificationService;

    @RabbitListener(queues = RabbitMQConfig.FRIEND_DELETE_QUEUE)
    @CircuitBreaker(name = "friendDeleteNotifyService", fallbackMethod = "fallbackNotify")
    public void handleFriendDeleteEvent(FriendDeleteEvent event) {
        String fromNickname = event.getFromNickname() != null ? event.getFromNickname() : "未知用户";
        String extra = new JSONObject()
                .set("fromUserId", event.getFromUid())
                .toString();
        notificationService.createNotification(
                event.getToUid(),
                NotificationTypeEnum.FRIEND_DELETE,
                "好友删除",
                fromNickname + " 已删除与你的好友关系",
                extra);
        log.info("[MQ] 好友删除通知已发送: from={}, to={}", event.getFromUid(), event.getToUid());
    }

    /** 熔断降级：仅记录日志，不影响删除好友主流程 */
    public void fallbackNotify(FriendDeleteEvent event, Exception e) {
        log.warn("[熔断] 好友删除通知降级: from={}, to={}, reason={}",
                event.getFromUid(), event.getToUid(), e.getMessage());
    }
}