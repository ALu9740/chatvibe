package com.chatvibe.module.auth.event;

import com.chatvibe.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 注册消息事件生产者
 *
 * @author Alu
 * @date 2026-07-15
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserRegisterEventProducer {
    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送用户注册事件
     * 注意：此方法应在事务提交后调用（使用 @TransactionalEventListener）
     */
    public void sendRegisterEvent(UserRegisterEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.CHAT_EXCHANGE,
                    "user.register.created",
                    event
            );
            log.info("[MQ] 发送注册事件: userId={}", event.getUserId());
        } catch (Exception e) {
            log.error("[MQ] 发送注册事件失败: {}", e.getMessage(), e);
        }
    }
}
