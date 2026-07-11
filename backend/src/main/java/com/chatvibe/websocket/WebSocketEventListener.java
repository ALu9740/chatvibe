package com.chatvibe.websocket;

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
 * 监听连接/断开事件（仅记录日志，不再修改用户在线状态）
 *
 * 说明：用户状态变更只发生在以下场景：
 *  - 用户手动修改状态（PUT /user/status）
 *  - 用户登录（置为在线 1）
 *  - 用户登出（置为离线 0）
 * 刷新页面触发的 WebSocket 断开/重连不再改变状态，避免覆盖用户手动设置的状态。
 *
 * @author Alu
 * @date 2026-06-27
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    /**
     * 连接成功事件: 仅记录日志，不修改状态
     */
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> attrs = accessor.getSessionAttributes();
        if (attrs != null && attrs.get("userId") instanceof Long userId) {
            String email = (String) attrs.get("email");
            log.info("[WebSocket] 用户上线: userId={}, email={}", userId, email);
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
