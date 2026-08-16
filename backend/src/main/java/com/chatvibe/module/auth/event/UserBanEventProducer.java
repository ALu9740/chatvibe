package com.chatvibe.module.auth.event;

import com.chatvibe.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 用户封禁/解封消息事件生产者
 *
 * @author Alu
 * @date 2026-08-16
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserBanEventProducer {
    private final RabbitTemplate rabbitTemplate;

    public void sendBanEvent(UserBanEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.CHAT_EXCHANGE,
                    "user.ban.status.changed",
                    event
            );
            log.info("[MQ] 发送用户封禁/解封事件: userId={}, banned={}", event.getUserId(), event.isBanned());
        } catch (Exception e) {
            log.error("[MQ] 发送用户封禁/解封事件失败: {}", e.getMessage(), e);
        }
    }
}
