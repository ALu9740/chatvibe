package com.chatvibe.module.group.event;

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
 * 群解散事件消费者：通过RabbitMQ异步通知被解散成员退出群聊
 *
 * @author Alu
 * @date 2026-07-21
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroupDissolveEventConsumer {
    private final NotificationService notificationService;

    @RabbitListener(queues = RabbitMQConfig.GROUP_DISSOLVE_QUEUE)
    @CircuitBreaker(name = "groupDissolveNotifyService", fallbackMethod = "fallbackNotify")
    public void handleGroupDissolveEvent(GroupDissolveEvent event) {
        for (Long memberId : event.getMemberIds()) {
            String extra = new JSONObject()
                    .set("conversationId", event.getConversationId())
                    .set("groupName", event.getGroupName())
                    .toString();
            notificationService.createNotification(memberId, NotificationTypeEnum.GROUP_DISSOLVE,
                    "群解散", "群聊 " + event.getGroupName() + " 已被群主解散", extra);
        }
        log.info("[MQ] 群解散通知已发送: convId={}, count={}", event.getConversationId(), event.getMemberIds().size());
    }

    public void fallbackNotify(GroupDissolveEvent event, Exception e) {
        log.warn("[熔断] 群解散通知降级: convId={}, reason={}", event.getConversationId(), e.getMessage());
    }
}