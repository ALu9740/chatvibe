package com.chatvibe.module.ai.memory;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.chatvibe.module.ai.entity.AiConversation;
import com.chatvibe.module.ai.mapper.AiConversationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于 {@code ai_conversation.context} JSON 列的 {@link ChatMemory} 实现。
 * <p>
 * 替代原 {@code AiController.updateContextAndPrompt}/{@code buildContextMessages}
 * 的手写 JSON 管理逻辑。Spring AI 通过 conversationId（即 ai_conversation.id 的字符串形式）
 * 自动读写上下文，并维护上下文窗口（保留最近 {@link #MAX_CONTEXT_MESSAGES} 条）。
 * <p>
 * context 列存储格式沿用原 JSON 数组：
 * {@code [{"role":"user","content":"..."},{"role":"assistant","content":"..."}]}
 *
 * @author Alu
 * @date 2026-07-24
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiConversationChatMemory implements ChatMemory {

    /** 上下文窗口大小（与原 MAX_CONTEXT_MESSAGES 对齐） */
    private static final int MAX_CONTEXT_MESSAGES = 20;

    private final AiConversationMapper aiConversationMapper;

    @Override
    public void add(String conversationId, List<Message> messages) {
        if (StrUtil.isBlank(conversationId) || messages == null || messages.isEmpty()) {
            return;
        }
        Long id = parseId(conversationId);
        if (id == null) {
            return;
        }
        AiConversation conv = aiConversationMapper.selectById(id);
        if (conv == null) {
            log.warn("[AI][ChatMemory] 会话不存在，忽略写入: convId={}", conversationId);
            return;
        }
        JSONArray contextArr = parseContext(conv.getContext());
        for (Message msg : messages) {
            JSONObject turn = new JSONObject();
            turn.set("role", msg.getMessageType().name().toLowerCase());
            turn.set("content", msg.getText());
            contextArr.add(turn);
        }
        // 维护上下文窗口：仅保留最近 MAX_CONTEXT_MESSAGES 条
        while (contextArr.size() > MAX_CONTEXT_MESSAGES) {
            contextArr.remove(0);
        }
        conv.setContext(contextArr.toString());
        aiConversationMapper.updateById(conv);
    }

    @Override
    public List<Message> get(String conversationId) {
        if (StrUtil.isBlank(conversationId)) {
            return List.of();
        }
        Long id = parseId(conversationId);
        if (id == null) {
            return List.of();
        }
        AiConversation conv = aiConversationMapper.selectById(id);
        if (conv == null || StrUtil.isBlank(conv.getContext())) {
            return List.of();
        }
        List<Message> messages = new ArrayList<>();
        JSONArray contextArr = parseContext(conv.getContext());
        for (Object obj : contextArr) {
            JSONObject turn = (JSONObject) obj;
            String role = turn.getStr("role");
            String content = turn.getStr("content");
            if (StrUtil.isBlank(content)) {
                continue;
            }
            if (MessageType.ASSISTANT.name().equalsIgnoreCase(role)) {
                messages.add(new AssistantMessage(content));
            } else {
                messages.add(new UserMessage(content));
            }
        }
        return messages;
    }

    @Override
    public void clear(String conversationId) {
        if (StrUtil.isBlank(conversationId)) {
            return;
        }
        Long id = parseId(conversationId);
        if (id == null) {
            return;
        }
        AiConversation conv = aiConversationMapper.selectById(id);
        if (conv == null) {
            return;
        }
        conv.setContext(null);
        aiConversationMapper.updateById(conv);
    }

    private Long parseId(String conversationId) {
        try {
            return Long.parseLong(conversationId);
        } catch (NumberFormatException e) {
            log.warn("[AI][ChatMemory] 非法 conversationId: {}", conversationId);
            return null;
        }
    }

    private JSONArray parseContext(String context) {
        if (StrUtil.isBlank(context)) {
            return new JSONArray();
        }
        try {
            return JSONUtil.parseArray(context);
        } catch (Exception e) {
            log.warn("[AI][ChatMemory] 解析上下文失败，重置为空: {}", e.getMessage());
            return new JSONArray();
        }
    }
}
