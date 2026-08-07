package com.chatvibe.module.admin.event;

import com.chatvibe.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 公告事件生产者
 * 在公告记录入库后，通过 MQ 异步发送通知创建任务
 *
 * @author Alu
 * @date 2026-08-07
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnnouncementEventProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送公告发布事件
     *
     * @param event 公告发布事件
     */
    public void sendAnnouncementPublishEvent(AnnouncementPublishEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.CHAT_EXCHANGE,
                    "announcement.publish.created",
                    event
            );
            log.info("[MQ] 发送公告发布事件: announcementId={}, title={}, targetCount={}",
                    event.getAnnouncementId(), event.getTitle(), event.getTargetUserIds().size());
        } catch (Exception e) {
            log.error("[MQ] 发送公告发布事件失败: announcementId={}, error={}",
                    event.getAnnouncementId(), e.getMessage(), e);
        }
    }
}
