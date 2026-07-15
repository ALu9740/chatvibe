package com.chatvibe.module.auth.event;

import com.chatvibe.config.RabbitMQConfig;
import com.chatvibe.module.user.enums.UserStatusEnum;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 登录消息事件消费者
 *
 * @author Alu
 * @date 2026-07-15
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserLoginEventConsumer {
    private final SimpMessagingTemplate messagingTemplate;

    @RabbitListener(queues = RabbitMQConfig.USER_LOGIN_QUEUE)
    @CircuitBreaker(name = "loginService", fallbackMethod = "fallbackForceLogout")
    public void handleLoginEvent(UserLoginEvent event) {
        messagingTemplate.convertAndSend(
                "/topic/user." + event.getUserId() + ".force-logout",
                Map.of("message", "当前账号已在其他设备登录，您已被强制下线",
                        "status", UserStatusEnum.ONLINE.getCode()));
        log.info("[MQ] 已发送强制下线通知: userId={}", event.getUserId());
    }

    /** 熔断降级：仅记录日志，不影响登录主流程 */
    public void fallbackForceLogout(UserLoginEvent event, Exception e) {
        log.warn("[熔断] 强制下线通知降级: userId={}, reason={}",
                event.getUserId(), e.getMessage());
    }
}