package com.chatvibe.module.auth.event;

import com.chatvibe.config.RabbitMQConfig;
import com.chatvibe.module.user.enums.UserStatusEnum;
import com.chatvibe.websocket.dto.WsStatusMessage;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 用户登出消息事件消费者
 * 异步广播用户离线状态到 WebSocket
 *
 * @author Alu
 * @date 2026-07-15
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserLogoutEventConsumer {
    private final SimpMessagingTemplate messagingTemplate;

    @RabbitListener(queues = RabbitMQConfig.USER_LOGOUT_QUEUE)
    @CircuitBreaker(name = "logoutService", fallbackMethod = "fallbackLogoutBroadcast")
    public void handleLogoutEvent(UserLogoutEvent event) {
        messagingTemplate.convertAndSend(
                "/topic/status",
                new WsStatusMessage(event.getUserId(), UserStatusEnum.OFFLINE.getCode()));
        log.info("[MQ] 已广播用户离线状态: userId={}", event.getUserId());
    }

    /** 熔断降级：仅记录日志，不影响登出主流程 */
    public void fallbackLogoutBroadcast(UserLogoutEvent event, Exception e) {
        log.warn("[熔断] 登出状态广播降级: userId={}, reason={}",
                event.getUserId(), e.getMessage());
    }
}