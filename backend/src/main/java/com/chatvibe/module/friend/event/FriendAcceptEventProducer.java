package com.chatvibe.module.friend.event;

import com.chatvibe.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
/**
 * 好友接受事件生产者
 *
 * @author Alu
 * @date 2026-07-20
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FriendAcceptEventProducer {
    private final RabbitTemplate rabbitTemplate;

    public void sendFriendAcceptEvent(FriendAcceptEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.CHAT_EXCHANGE,
                    "friend.accept.created",
                    event
            );
            log.info("[MQ] 发送好友接受事件: requestId={}, from={}, to={}",
                    event.getRequestId(), event.getFromUid(), event.getToUid());
        } catch (Exception e) {
            log.error("[MQ] 发送好友接受事件失败: {}", e.getMessage(), e);
        }
    }
}