package com.chatvibe.module.friend.event;

import com.chatvibe.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 好友删除事件生产者
 *
 * @author Alu
 * @date 2026-07-20
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FriendDeleteEventProducer {
    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送好友删除事件
     * 注意：此方法应在事务提交后调用（使用 TransactionSynchronizationManager）
     */
    public void sendFriendDeleteEvent(FriendDeleteEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.CHAT_EXCHANGE,
                    "friend.delete.created",
                    event
            );
            log.info("[MQ] 发送好友删除事件: from={}, to={}", event.getFromUid(), event.getToUid());
        } catch (Exception e) {
            log.error("[MQ] 发送好友删除事件失败: {}", e.getMessage(), e);
        }
    }
}