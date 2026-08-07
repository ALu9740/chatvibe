package com.chatvibe.module.admin.event;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.chatvibe.config.RabbitMQConfig;
import com.chatvibe.module.notification.enums.NotificationTypeEnum;
import com.chatvibe.module.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 公告事件消费者
 * 消费公告发布事件，为每个目标用户创建系统通知（落库 + WebSocket 实时推送）
 *
 * @author Alu
 * @date 2026-08-07
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnnouncementEventConsumer {

    private final NotificationService notificationService;

    @RabbitListener(queues = RabbitMQConfig.ANNOUNCEMENT_QUEUE)
    public void handleAnnouncementPublishEvent(AnnouncementPublishEvent event) {
        log.info("[MQ] 收到公告发布事件: announcementId={}, targetCount={}",
                event.getAnnouncementId(), event.getTargetUserIds().size());

        int successCount = 0;
        int failCount = 0;
        for (Long userId : event.getTargetUserIds()) {
            try {
                // extra 中携带 announcementId，NotificationServiceImpl 会解析并设置到实体上
                JSONObject extra = new JSONObject()
                        .set("announcementId", event.getAnnouncementId());
                notificationService.createNotification(
                        userId,
                        NotificationTypeEnum.SYSTEM,
                        event.getTitle(),
                        event.getContent(),
                        extra.toString()
                );
                successCount++;
            } catch (Exception e) {
                log.error("[MQ] 公告通知创建失败: userId={}, announcementId={}, error={}",
                        userId, event.getAnnouncementId(), e.getMessage());
                failCount++;
            }
        }
        log.info("[MQ] 公告通知处理完成: announcementId={}, success={}, fail={}",
                event.getAnnouncementId(), successCount, failCount);
    }
}
