package com.chatvibe.config;

import com.chatvibe.websocket.JwtHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket (STOMP) 配置
 *
 * @author Alu
 * @date 2026-06-27
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    /**
     * 注册 STOMP 端点
     * 客户端通过 SockJS 连接 /ws-chat
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-chat")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    /**
     * 配置消息代理
     * - /app: 客户端发送消息前缀
     * - /topic: 广播订阅前缀
     * - /user: 点对点订阅前缀
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 客户端发送消息到服务端的前缀
        registry.setApplicationDestinationPrefixes("/app");
        // 服务端广播消息的前缀
        registry.enableSimpleBroker("/topic", "/user");
        // 点对点消息前缀
        registry.setUserDestinationPrefix("/user");
    }
}
