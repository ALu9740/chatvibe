package com.chatvibe.module.friend.event;

import com.chatvibe.config.RabbitMQConfig;
import com.chatvibe.module.notification.enums.NotificationTypeEnum;
import com.chatvibe.module.notification.service.NotificationService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 好友请求事件消费者：异步创建通知
 *
 * @author Alu
 * @date 2026-07-20
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FriendRequestEventConsumer {
    private final NotificationService notificationService;

    @RabbitListener(queues = RabbitMQConfig.FRIEND_REQUEST_QUEUE)
    @CircuitBreaker(name = "friendNotifyService", fallbackMethod = "fallbackNotify")
    public void handleFriendRequestEvent(FriendRequestEvent event) {
        String fromNickname = event.getFromNickname() != null ? event.getFromNickname() : "未知用户";
        String extra = new cn.hutool.json.JSONObject()
                .set("fromUserId", event.getFromUid())
                .set("fromNickname", fromNickname)
                .set("requestId", event.getRequestId())
                .toString();
        notificationService.createNotification(
                event.getToUid(),
                NotificationTypeEnum.FRIEND_REQUEST,
                "好友请求",
                fromNickname + " 请求添加你为好友",
                extra);
        log.info("[MQ] 好友请求通知已发送: from={}, to={}", event.getFromUid(), event.getToUid());
    }

    /** 熔断降级：仅记录日志，不影响好友请求主流程 */
    public void fallbackNotify(FriendRequestEvent event, Exception e) {
        log.warn("[熔断] 好友请求通知降级: from={}, to={}, reason={}",
                event.getFromUid(), event.getToUid(), e.getMessage());
    }
}