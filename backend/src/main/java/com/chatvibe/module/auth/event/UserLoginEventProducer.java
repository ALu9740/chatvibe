package com.chatvibe.module.auth.event;

import com.chatvibe.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 登录消息事件生产者
 *
 * @author Alu
 * @date 2026-07-15
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserLoginEventProducer {
    private final RabbitTemplate rabbitTemplate;

    public void sendLoginEvent(UserLoginEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.CHAT_EXCHANGE,
                    "user.login.created",
                    event
            );
            log.info("[MQ] 发送登录事件: userId={}", event.getUserId());
        } catch (Exception e) {
            log.error("[MQ] 发送登录事件失败: {}", e.getMessage(), e);
        }
    }
}