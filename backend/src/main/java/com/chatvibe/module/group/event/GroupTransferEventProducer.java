package com.chatvibe.module.group.event;

import com.chatvibe.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 群转让事件生产者
 *
 * @author Alu
 * @date 2026-07-21
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroupTransferEventProducer {
    private final RabbitTemplate rabbitTemplate;

    public void sendGroupTransferEvent(GroupTransferEvent event) {
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.CHAT_EXCHANGE, "group.transfer.created", event);
            log.info("[MQ] 发送转让群主事件: convId={}, newOwner={}", event.getConversationId(), event.getNewOwnerId());
        } catch (Exception e) {
            log.error("[MQ] 发送转让群主事件失败: {}", e.getMessage(), e);
        }
    }
}