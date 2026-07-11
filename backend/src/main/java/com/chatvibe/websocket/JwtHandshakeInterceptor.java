package com.chatvibe.websocket;

import com.chatvibe.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;

/**
 * WebSocket 握手拦截器
 * 从 query param 提取 JWT token 并校验
 *
 * @author Alu
 * @date 2026-06-27
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private static final String TOKEN_PARAM = "token";

    private final JwtUtil jwtUtil;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String token = extractToken(request);
        if (token == null) {
            log.warn("[WebSocket] 握手失败: 缺少 token");
            return false;
        }
        if (!jwtUtil.validateToken(token)) {
            log.warn("[WebSocket] 握手失败: token 无效");
            return false;
        }
        Long userId = jwtUtil.getUserIdFromToken(token);
        String email = jwtUtil.getEmailFromToken(token);
        // 将用户信息放入 attributes，供后续使用
        attributes.put("userId", userId);
        attributes.put("email", email);
        log.info("[WebSocket] 握手成功: userId={}, email={}", userId, email);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // 握手后无需处理
    }

    /**
     * 从请求 URI 的 query param 中提取 token
     */
    private String extractToken(ServerHttpRequest request) {
        URI uri = request.getURI();
        String query = uri.getQuery();
        if (query == null) {
            return null;
        }
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && TOKEN_PARAM.equals(kv[0])) {
                return kv[1];
            }
        }
        return null;
    }
}
