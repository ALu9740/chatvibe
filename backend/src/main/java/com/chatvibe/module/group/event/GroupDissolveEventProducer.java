package com.chatvibe.module.group.event;

import com.chatvibe.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 群解散事件生产者
 *
 * @author Alu
 * @date 2026-07-21
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroupDissolveEventProducer {
    private final RabbitTemplate rabbitTemplate;

    public void sendGroupDissolveEvent(GroupDissolveEvent event) {
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.CHAT_EXCHANGE, "group.dissolve.created", event);
            log.info("[MQ] 发送群解散事件: convId={}, memberCount={}", event.getConversationId(), event.getMemberIds().size());
        } catch (Exception e) {
            log.error("[MQ] 发送群解散事件失败: {}", e.getMessage(), e);
        }
    }
}