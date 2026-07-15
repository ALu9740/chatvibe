package com.chatvibe.module.auth.event;

import com.chatvibe.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 密码重置消息事件生产者
 *
 * @author Alu
 * @date 2026-07-15
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserPasswordResetEventProducer {
    private final RabbitTemplate rabbitTemplate;

    public void sendPasswordResetEvent(UserPasswordResetEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.CHAT_EXCHANGE,
                    "user.password.reset.created",
                    event
            );
            log.info("[MQ] 发送密码重置事件: userId={}", event.getUserId());
        } catch (Exception e) {
            log.error("[MQ] 发送密码重置事件失败: {}", e.getMessage(), e);
        }
    }
}