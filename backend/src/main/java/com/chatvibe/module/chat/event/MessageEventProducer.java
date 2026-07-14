package com.chatvibe.module.chat.event;

import com.chatvibe.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 消息事件生产者
 * 将WebSocket接收到的消息事件发送到RabbitMQ
 *
 * @author Alu
 * @date 2026-07-14
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageEventProducer {
    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送消息推送事件
     */
    public void sendPushEvent(MessageEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.CHAT_EXCHANGE,
                    "chat.message.push",
                    event
            );
            log.debug("[MQ] 发送消息推送事件: msgId={}, convId={}", event.getMessageId(), event.getConversationId());
        } catch (Exception e) {
            log.error("[MQ] 发送消息事件失败: {}", e.getMessage(), e);
        }
    }
}
