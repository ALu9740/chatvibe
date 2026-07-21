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
 * 群邀请事件消费者：通过RabbitMQ异步通知被邀请成员加入群聊
 *
 * @author Alu
 * @date 2026-07-21
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroupTransferEventConsumer {
    private final NotificationService notificationService;

    @RabbitListener(queues = RabbitMQConfig.GROUP_TRANSFER_QUEUE)
    @CircuitBreaker(name = "groupTransferNotifyService", fallbackMethod = "fallbackNotify")
    public void handleGroupTransferEvent(GroupTransferEvent event) {
        String extra = new JSONObject()
                .set("conversationId", event.getConversationId())
                .set("groupName", event.getGroupName())
                .toString();
        notificationService.createNotification(event.getNewOwnerId(), NotificationTypeEnum.GROUP_TRANSFER,
                "群主转让", "你已成为群聊 " + event.getGroupName() + " 的新群主", extra);
        log.info("[MQ] 转让群主通知已发送: convId={}, newOwner={}", event.getConversationId(), event.getNewOwnerId());
    }

    public void fallbackNotify(GroupTransferEvent event, Exception e) {
        log.warn("[熔断] 转让群主通知降级: convId={}, reason={}", event.getConversationId(), e.getMessage());
    }
}