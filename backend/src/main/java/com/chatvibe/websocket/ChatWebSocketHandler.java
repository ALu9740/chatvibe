package com.chatvibe.websocket;

import com.chatvibe.module.chat.dto.SendMessageDTO;
import com.chatvibe.module.chat.service.ChatService;
import com.chatvibe.security.LoginUser;
import com.chatvibe.websocket.dto.WsMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * 聊天 WebSocket 消息处理器
 * 接收 /app/chat.send STOMP 消息，落库并广播到对应会话 topic
 * 推送统一由 ChatService.sendMessage 负责，避免重复推送
 *
 * @author Alu
 * @date 2026-06-27
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler {

    private final ChatService chatService;

    /**
     * 处理客户端发送到 /app/chat.send 的消息
     *
     * @param wsMessage 消息体
     * @param accessor  STOMP 头访问器
     */
    @MessageMapping("/chat.send")
    public void handleChatMessage(@Payload WsMessage wsMessage, StompHeaderAccessor accessor) {
        Long senderId = resolveSenderId(accessor);
        if (senderId == null) {
            log.warn("[WebSocket] 无法解析发送者ID, 丢弃消息");
            return;
        }
        wsMessage.setSenderId(senderId);
        wsMessage.setTimestamp(System.currentTimeMillis());

        try {
            // 落库 + 更新未读 + 广播 (复用 ChatService 逻辑, 推送统一由 ChatService 负责, 避免重复)
            SendMessageDTO dto = new SendMessageDTO();
            dto.setConversationId(wsMessage.getConversationId());
            dto.setType(wsMessage.getType() == null ? 0 : wsMessage.getType());
            dto.setContent(wsMessage.getContent());
            dto.setExtra(wsMessage.getExtra());
            chatService.sendMessage(dto, senderId);
            log.debug("[WebSocket] 消息已处理: convId={}, senderId={}",
                    wsMessage.getConversationId(), senderId);
        } catch (Exception e) {
            log.error("[WebSocket] 处理消息失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 从 STOMP 上下文解析发送者ID
     */
    private Long resolveSenderId(StompHeaderAccessor accessor) {
        // 优先从握手阶段放入 attributes 的 userId 获取
        Object userIdAttr = accessor.getSessionAttributes() != null
                ? accessor.getSessionAttributes().get("userId") : null;
        if (userIdAttr instanceof Long uid) {
            return uid;
        }
        // 兜底: 从 Principal 获取
        Principal principal = accessor.getUser();
        if (principal instanceof Authentication auth && auth.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser.getId();
        }
        return null;
    }
}
