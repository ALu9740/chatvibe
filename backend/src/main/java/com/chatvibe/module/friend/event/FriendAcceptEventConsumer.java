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
 * 好友接受事件消费者：异步创建通知
 *
 * @author Alu
 * @date 2026-07-20
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FriendAcceptEventConsumer {
    private final NotificationService notificationService;

    @RabbitListener(queues = RabbitMQConfig.FRIEND_ACCEPT_QUEUE)
    @CircuitBreaker(name = "friendAcceptNotifyService", fallbackMethod = "fallbackNotify")
    public void handleFriendAcceptEvent(FriendAcceptEvent event) {
        String acceptNickname = event.getAcceptNickname() != null ? event.getAcceptNickname() : "未知用户";
        String extra = new JSONObject()
                .set("toUserId", event.getToUid())
                .toString();
        notificationService.createNotification(
                event.getFromUid(),
                NotificationTypeEnum.FRIEND_ACCEPT,
                "好友接受",
                acceptNickname + " 已接受你的好友请求",
                extra);
        log.info("[MQ] 好友接受通知已发送: from={}, to={}", event.getFromUid(), event.getToUid());
    }

    public void fallbackNotify(FriendAcceptEvent event, Exception e) {
        log.warn("[熔断] 好友接受通知降级: requestId={}, reason={}",
                event.getRequestId(), e.getMessage());
    }
}