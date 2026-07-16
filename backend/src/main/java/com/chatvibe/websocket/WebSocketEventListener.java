package com.chatvibe.websocket;

import com.chatvibe.module.user.enums.UserStatusEnum;
import com.chatvibe.module.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;

/**
 * WebSocket 事件监听器
 *
 * 连接事件：如果用户当前状态为离线(0)，则恢复为在线(1)并广播。
 *   刷新页面后 WebSocket 重连，此逻辑确保用户状态正确（修复 Redis 残留旧离线值的问题）。
 *   不会覆盖用户手动设置的"忙碌(2)"或"离开(3)"状态。
 *
 * 断开事件：仅记录日志，不修改状态。
 *   刷新页面会触发断开，此时不应将用户置为离线。
 *
 * @author Alu
 * @date 2026-06-27
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final UserService userService;

    /**
     * 连接成功事件：若用户当前为离线则恢复为在线（不覆盖忙碌/离开状态）
     */
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> attrs = accessor.getSessionAttributes();
        if (attrs != null && attrs.get("userId") instanceof Long userId) {
            String email = (String) attrs.get("email");
            log.info("[WebSocket] 用户上线: userId={}, email={}", userId, email);
            // 如果用户当前状态为离线，恢复为在线（刷新后重连的场景）
            Integer currentStatus = userService.getUserStatus(userId);
            if (currentStatus != null && currentStatus == UserStatusEnum.OFFLINE.getCode()) {
                userService.updateStatus(userId, UserStatusEnum.ONLINE.getCode());
                log.info("[WebSocket] 用户刷新后恢复在线状态: userId={}", userId);
            }
        }
    }

    /**
     * 断开连接事件: 仅记录日志，不修改状态
     * （刷新页面会触发断开，此时不应将用户置为离线）
     */
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> attrs = accessor.getSessionAttributes();
        if (attrs != null && attrs.get("userId") instanceof Long userId) {
            log.info("[WebSocket] 用户下线(可能因刷新): userId={}", userId);
        }
    }
}
