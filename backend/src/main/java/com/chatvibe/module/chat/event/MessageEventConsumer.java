package com.chatvibe.module.chat.event;

import com.chatvibe.config.RabbitMQConfig;
import com.chatvibe.module.chat.entity.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;


/**
 * 消息事件消费者
 * 从 RabbitMQ 接收事件，执行 WebSocket 推送
 *
 * @author Alu
 * @date 2026-07-14
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageEventConsumer {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 消费消息推送事件，通过 WebSocket 推送给会话成员
     */
    @RabbitListener(queues = RabbitMQConfig.CHAT_MESSAGE_QUEUE)
    public void handlePushEvent(MessageEvent event) {
        log.info("[MQ] 收到消息推送事件: msgId={}, convId={}, senderId={}",
                event.getMessageId(), event.getConversationId(), event.getSenderId());
        try {
            // 重建 Message 对象用于 WebSocket 推送
            Message message = new Message();
            message.setId(event.getMessageId());
            message.setConversationId(event.getConversationId());
            message.setSenderId(event.getSenderId());
            message.setType(event.getType());
            message.setContent(event.getContent());
            message.setExtra(event.getExtra());
            message.setStatus(event.getStatus());
            message.setSenderName(event.getSenderName());
            message.setSenderAvatar(event.getSenderAvatar());
            // 恢复 createdAt（从毫秒时间戳转换为 LocalDateTime）
            if (event.getCreatedAt() != null) {
                message.setCreatedAt(LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(event.getCreatedAt()), ZoneId.systemDefault()));
            }

            String destination = "/topic/conversation." + event.getConversationId();
            messagingTemplate.convertAndSend(destination, message);
            log.info("[MQ] WebSocket 推送完成: destination={}, msgId={}", destination, event.getMessageId());
        } catch (Exception e) {
            log.error("[MQ] 消费消息推送事件失败: msgId={}, convId={}, error={}",
                    event.getMessageId(), event.getConversationId(), e.getMessage(), e);
            // 抛出异常会触发 RabbitMQ 重试机制
            throw new RuntimeException(e);
        }
    }
}
