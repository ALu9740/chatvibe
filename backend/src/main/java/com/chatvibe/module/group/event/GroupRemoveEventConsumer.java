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
 * 群移除成员事件消费者：通过RabbitMQ异步通知被移除的成员退出群聊
 *
 * @author Alu
 * @date 2026-07-21
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroupRemoveEventConsumer {
    private final NotificationService notificationService;

    @RabbitListener(queues = RabbitMQConfig.GROUP_REMOVE_QUEUE)
    @CircuitBreaker(name = "groupRemoveNotifyService", fallbackMethod = "fallbackNotify")
    public void handleGroupRemoveEvent(GroupRemoveEvent event) {
        String extra = new JSONObject()
                .set("conversationId", event.getConversationId())
                .set("groupName", event.getGroupName())
                .toString();
        notificationService.createNotification(event.getRemovedUserId(), NotificationTypeEnum.GROUP_REMOVE,
                "被移除群", "你已被移出群聊 " + event.getGroupName(), extra);
        log.info("[MQ] 移除成员通知已发送: convId={}, userId={}", event.getConversationId(), event.getRemovedUserId());
    }

    public void fallbackNotify(GroupRemoveEvent event, Exception e) {
        log.warn("[熔断] 移除成员通知降级: convId={}, reason={}", event.getConversationId(), e.getMessage());
    }
}