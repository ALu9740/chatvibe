package com.chatvibe.websocket;

import com.chatvibe.module.chat.dto.SendMessageDTO;
import com.chatvibe.module.chat.entity.Message;
import com.chatvibe.module.chat.service.ChatService;
import com.chatvibe.security.LoginUser;
import com.chatvibe.websocket.dto.WsMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * 聊天 WebSocket 消息处理器
 * 接收 /app/chat.send STOMP 消息，落库并直接广播到对应会话 topic
 * 广播由本 Handler 直接执行（SimpMessagingTemplate），不经过 RabbitMQ，确保低延迟和高可靠
 *
 * 注意：必须使用 @Controller 而非 @Component，因为 Spring STOMP 的
 * SimpAnnotationMethodMessageHandler.isHandler() 只扫描 @Controller 注解的 Bean
 * 来注册 @MessageMapping 方法。使用 @Component 会导致 "No matching message handler methods"。
 *
 * @author Alu
 * @date 2026-06-27
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatWebSocketHandler {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

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
        log.info("[WebSocket] 收到发送消息请求: convId={}, senderId={}, type={}, contentLen={}",
                wsMessage.getConversationId(), senderId, wsMessage.getType(),
                wsMessage.getContent() != null ? wsMessage.getContent().length() : 0);

        try {
            // 落库 + 更新未读 (广播由本 Handler 直接执行，不经 MQ)
            SendMessageDTO dto = new SendMessageDTO();
            dto.setConversationId(wsMessage.getConversationId());
            dto.setType(wsMessage.getType() == null ? 0 : wsMessage.getType());
            dto.setContent(wsMessage.getContent());
            dto.setExtra(wsMessage.getExtra());
            Message saved = chatService.sendMessage(dto, senderId);
            log.info("[WebSocket] 消息处理完成: msgId={}, convId={}, senderId={}",
                    saved.getId(), wsMessage.getConversationId(), senderId);

            // 直接通过 STOMP 广播到会话 topic（所有订阅了该 topic 的客户端都会收到，包括发送者自己）
            String destination = "/topic/conversation." + wsMessage.getConversationId();
            messagingTemplate.convertAndSend(destination, saved);
            log.info("[WebSocket] STOMP 广播完成: destination={}, msgId={}", destination, saved.getId());
        } catch (Exception e) {
            log.error("[WebSocket] 处理消息失败: convId={}, senderId={}, error={}",
                    wsMessage.getConversationId(), senderId, e.getMessage(), e);
        }
    }

    /**
     * 处理客户端发送到 /app/chat.read 的已读回执
     * 通过 WebSocket 标记会话已读，无需走 HTTP REST
     *
     * @param payload  包含 conversationId 的消息体
     * @param accessor STOMP 头访问器
     */
    @MessageMapping("/chat.read")
    public void handleReadReceipt(@Payload java.util.Map<String, Object> payload, StompHeaderAccessor accessor) {
        Long userId = resolveSenderId(accessor);
        if (userId == null) {
            log.warn("[WebSocket] 已读回执: 无法解析用户ID, 丢弃");
            return;
        }
        Object convIdObj = payload.get("conversationId");
        if (convIdObj == null) {
            log.warn("[WebSocket] 已读回执: 缺少 conversationId");
            return;
        }
        Long conversationId;
        try {
            conversationId = Long.valueOf(convIdObj.toString());
        } catch (NumberFormatException e) {
            log.warn("[WebSocket] 已读回执: conversationId 格式错误: {}", convIdObj);
            return;
        }
        try {
            chatService.markAsRead(conversationId, userId);
            log.debug("[WebSocket] 已读回执已处理: convId={}, userId={}", conversationId, userId);
        } catch (Exception e) {
            log.error("[WebSocket] 已读回执处理失败: {}", e.getMessage(), e);
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
