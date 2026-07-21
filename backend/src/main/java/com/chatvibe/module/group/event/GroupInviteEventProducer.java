package com.chatvibe.module.group.event;

import com.chatvibe.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 群邀请事件生产者
 *
 * @author Alu
 * @date 2026-07-21
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroupInviteEventProducer {
    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送群邀请事件
     * 注意：此方法应在事务提交后调用
     */
    public void sendGroupInviteEvent(GroupInviteEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.CHAT_EXCHANGE,
                    "group.invite.created",
                    event
            );
            log.info("[MQ] 发送群邀请事件: convId={}, memberCount={}",
                    event.getConversationId(), event.getMemberIds().size());
        } catch (Exception e) {
            log.error("[MQ] 发送群邀请事件失败: {}", e.getMessage(), e);
        }
    }
}