package com.chatvibe.module.group.event;

import com.chatvibe.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 群移除成员事件生产者
 *
 * @author Alu
 * @date 2026-07-21
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroupRemoveEventProducer {
    private final RabbitTemplate rabbitTemplate;

    public void sendGroupRemoveEvent(GroupRemoveEvent event) {
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.CHAT_EXCHANGE, "group.remove.created", event);
            log.info("[MQ] 发送移除成员事件: convId={}, userId={}", event.getConversationId(), event.getRemovedUserId());
        } catch (Exception e) {
            log.error("[MQ] 发送移除成员事件失败: {}", e.getMessage(), e);
        }
    }
}