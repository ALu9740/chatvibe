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
public class GroupInviteEventConsumer {
    private final NotificationService notificationService;

    @RabbitListener(queues = RabbitMQConfig.GROUP_INVITE_QUEUE)
    @CircuitBreaker(name = "groupInviteNotifyService", fallbackMethod = "fallbackNotify")
    public void handleGroupInviteEvent(GroupInviteEvent event) {
        String ownerName = event.getOwnerNickname() != null ? event.getOwnerNickname() : "未知用户";
        for (Long memberId : event.getMemberIds()) {
            String extra = new JSONObject()
                    .set("conversationId", event.getConversationId())
                    .set("groupName", event.getGroupName())
                    .toString();
            notificationService.createNotification(memberId, NotificationTypeEnum.GROUP_INVITE,
                    "群邀请", ownerName + " 邀请你加入群聊 " + event.getGroupName(), extra);
        }
        log.info("[MQ] 群邀请通知已发送: convId={}, count={}",
                event.getConversationId(), event.getMemberIds().size());
    }

    /** 熔断降级：仅记录日志，不影响建群主流程 */
    public void fallbackNotify(GroupInviteEvent event, Exception e) {
        log.warn("[熔断] 群邀请通知降级: convId={}, reason={}",
                event.getConversationId(), e.getMessage());
    }
}