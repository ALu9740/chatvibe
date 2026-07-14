package com.chatvibe.module.ai.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chatvibe.common.result.Result;
import com.chatvibe.common.result.ResultCode;
import com.chatvibe.common.exception.BusinessException;
import com.chatvibe.module.ai.dto.AiChatDTO;
import com.chatvibe.module.ai.dto.CreateAiConversationDTO;
import com.chatvibe.module.ai.dto.SendAiMessageDTO;
import com.chatvibe.module.ai.entity.AiConversation;
import com.chatvibe.module.ai.mapper.AiConversationMapper;
import com.chatvibe.module.ai.service.AICiruitBreakerService;
import com.chatvibe.module.ai.service.AiService;
import com.chatvibe.module.ai.service.impl.AiFallbackServiceImpl;
import com.chatvibe.module.ai.vo.AiConversationVO;
import com.chatvibe.module.chat.entity.Conversation;
import com.chatvibe.module.chat.entity.Message;
import com.chatvibe.module.chat.enums.MessageTypeEnum;
import com.chatvibe.module.chat.mapper.ConversationMapper;
import com.chatvibe.module.chat.mapper.MessageMapper;
import com.chatvibe.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * AI 对话接口
 * 提供 SSE 流式对话 + REST CRUD 接口
 *
 * @author Alu
 * @date 2026-07-01
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private static final String RATE_LIMIT_KEY_PREFIX = "ai:limit:";
    private static final int RATE_LIMIT_PER_MINUTE = 20;
    private static final Duration RATE_LIMIT_TTL = Duration.ofMinutes(1);
    private static final long AI_SENDER_ID = 0L;
    private static final int MAX_CONTEXT_MESSAGES = 20;
    private static final long SYNC_TIMEOUT_SECONDS = 60;
    /** AI 回复发送者昵称与头像（senderId=0 无 user 记录，需手动填充） */
    private static final String AI_SENDER_NAME = "Vibe助手";
    private static final String AI_SENDER_AVATAR = "🤖";

    private final AiService aiService;
    private final AiFallbackServiceImpl aiFallbackService;
    private final AiConversationMapper aiConversationMapper;
    private final MessageMapper messageMapper;
    private final ConversationMapper conversationMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final AICiruitBreakerService aiCiruitBreakerService;

    // ============================================================
    // SSE 流式对话
    // ============================================================

    /**
     * AI 流式对话
     * 返回 text/event-stream
     * <p>
     * 统一流程：用户提问已由前端通过 sendMessage 落库并广播给会话成员，
     * 此接口仅负责生成 AI 回复、落库到 chatConversationId 对应的会话、并 WebSocket 广播给所有成员。
     * 上下文从该会话的历史消息中构建（排除当前刚保存的提问以避免重复）。
     */
    @PostMapping(value = "/chat", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter chat(@Valid @RequestBody AiChatDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();

        // 限流: 每分钟 20 次
        checkRateLimit(userId);

        // 创建 SseEmitter (超时 3 分钟)
        SseEmitter emitter = new SseEmitter(3 * 60 * 1000L);
        StringBuilder fullResponse = new StringBuilder();
        AtomicReference<Boolean> completed = new AtomicReference<>(false);

        // 聊天会话ID（私聊/群聊/独立AI会话），用于落库 AI 回复 + WebSocket 广播
        Long chatConvId = dto.getChatConversationId();

        // 构建上下文消息列表（从会话历史消息中提取，排除当前提问）
        List<Map<String, String>> contextMessages = buildContextFromMessages(chatConvId, userId, dto.getPrompt());

        // 通过熔断器调用 AI 服务，熔断时自动走兜底
        aiCiruitBreakerService.chatStreamWithCircuitBreaker(
                dto.getPrompt(),
                userId,
                contextMessages,
                // onToken
                token -> {
                    try {
                        if (Boolean.TRUE.equals(completed.get())) {
                            return;
                        }
                        fullResponse.append(token);
                        emitter.send(SseEmitter.event().data(token));
                    } catch (IOException e) {
                        log.warn("[AI] SseEmitter 发送失败: {}", e.getMessage());
                        emitter.completeWithError(e);
                    }
                },
                // onError: 熔断器会自动降级，这里只需记录日志
                error -> {
                    log.warn("[AI] 主服务异常（熔断器将处理降级）: {}", error.getMessage());
                    // 如果熔断器未触发降级（如首次失败），手动走兜底
                    if (!completed.get()) {
                        runFallback(dto, userId, chatConvId, emitter, fullResponse, completed);
                    }
                },
                // onComplete
                () -> {
                    if (completed.compareAndSet(false, true)) {
                        try {
                            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                            emitter.complete();
                            saveAndBroadcastAiReply(chatConvId, fullResponse.toString());
                            log.info("[AI] 对话完成: userId={}, convId={}, respLen={}", userId, chatConvId, fullResponse.length());
                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    }
                }
        );

        // 超时和错误处理
        emitter.onTimeout(() -> {
            completed.set(true);
            log.warn("[AI] SseEmitter 超时: userId={}", userId);
        });
        emitter.onError(e -> {
            completed.set(true);
            log.warn("[AI] SseEmitter 异常: userId={}, msg={}", userId, e.getMessage());
        });

        return emitter;
    }

    /**
     * 运行兜底服务
     */
    private void runFallback(AiChatDTO dto, Long userId, Long chatConvId,
                             SseEmitter emitter, StringBuilder fullResponse,
                             AtomicReference<Boolean> completed) {
        List<Map<String, String>> contextMessages = buildContextFromMessages(chatConvId, userId, dto.getPrompt());
        aiFallbackService.chatStream(
                dto.getPrompt(),
                userId,
                contextMessages,
                token -> {
                    try {
                        if (Boolean.TRUE.equals(completed.get())) {
                            return;
                        }
                        fullResponse.append(token);
                        emitter.send(SseEmitter.event().data(token));
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                },
                error -> {
                    if (completed.compareAndSet(false, true)) {
                        try {
                            emitter.send(SseEmitter.event().name("error").data("AI 服务不可用"));
                            emitter.complete();
                        } catch (IOException ignored) {
                            emitter.completeWithError(error);
                        }
                    }
                },
                () -> {
                    if (completed.compareAndSet(false, true)) {
                        try {
                            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                            emitter.complete();
                            saveAndBroadcastAiReply(chatConvId, fullResponse.toString());
                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    }
                }
        );
    }

    // ============================================================
    // REST CRUD 接口
    // ============================================================

    /**
     * 获取 AI 对话列表
     */
    @GetMapping("/conversations")
    public Result<List<AiConversationVO>> getAiConversations() {
        Long userId = SecurityUtils.getCurrentUserId();
        List<AiConversation> list = aiConversationMapper.selectList(
                new LambdaQueryWrapper<AiConversation>()
                        .eq(AiConversation::getUserId, userId)
                        .orderByDesc(AiConversation::getUpdatedAt)
                        .orderByDesc(AiConversation::getId));
        return Result.success(list.stream().map(this::toVO).collect(Collectors.toList()));
    }

    /**
     * 创建新的 AI 对话
     */
    @PostMapping("/conversations")
    public Result<AiConversationVO> createAiConversation(@RequestBody(required = false) CreateAiConversationDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        AiConversation conv = new AiConversation();
        conv.setUserId(userId);
        conv.setTitle(StrUtil.isNotBlank(dto == null ? null : dto.getTitle()) ? dto.getTitle() : "AI 对话");
        conv.setProvider(aiService.getProvider());
        aiConversationMapper.insert(conv);
        log.info("[AI] 创建新对话: convId={}, userId={}", conv.getId(), userId);
        return Result.success(toVO(conv));
    }

    /**
     * 获取 AI 对话历史消息
     */
    @GetMapping("/conversations/{conversationId}/messages")
    public Result<List<Message>> getAiMessages(@PathVariable Long conversationId) {
        Long userId = SecurityUtils.getCurrentUserId();
        // 校验对话归属
        AiConversation conv = getOwnedAiConversation(conversationId, userId);
        List<Message> messages = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, conv.getId())
                        .orderByAsc(Message::getId));
        return Result.success(messages);
    }

    /**
     * 发送 AI 对话请求(非流式,作为兜底)
     * 保存用户消息 → 调用 AI → 保存 AI 回复 → 返回 AI 回复消息
     */
    @PostMapping("/conversations/{conversationId}/messages")
    public Result<Message> sendAiMessage(@PathVariable Long conversationId,
                                         @Valid @RequestBody SendAiMessageDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        AiConversation conv = getOwnedAiConversation(conversationId, userId);

        // 1. 落库用户消息
        Message userMsg = new Message();
        userMsg.setConversationId(conv.getId());
        userMsg.setSenderId(userId);
        userMsg.setType(MessageTypeEnum.TYPE_TEXT.getCode());
        userMsg.setContent(dto.getContent());
        userMsg.setStatus(0);
        messageMapper.insert(userMsg);

        // 2. 同步调用 AI 服务(收集完整回复)
        StringBuilder fullResponse = new StringBuilder();
        AtomicReference<Throwable> errorRef = new AtomicReference<>(null);
        CountDownLatch latch = new CountDownLatch(1);

        List<Map<String, String>> contextMessages = buildContextMessages(conv);

        aiService.chatStream(
                dto.getContent(),
                userId,
                contextMessages,
                fullResponse::append,
                error -> {
                    errorRef.set(error);
                    latch.countDown();
                },
                latch::countDown
        );

        try {
            if (!latch.await(SYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "AI 响应超时");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "AI 调用被中断");
        }

        // 主服务失败时尝试兜底
        if (errorRef.get() != null && fullResponse.length() == 0) {
            log.warn("[AI] 非流式调用主服务失败，切换兜底: {}", errorRef.get().getMessage());
            CountDownLatch fallbackLatch = new CountDownLatch(1);
            aiFallbackService.chatStream(
                    dto.getContent(),
                    userId,
                    contextMessages,
                    fullResponse::append,
                    error -> fallbackLatch.countDown(),
                    fallbackLatch::countDown
            );
            try {
                if (!fallbackLatch.await(SYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "AI 兜底响应超时");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "AI 兜底调用被中断");
            }
        }

        if (fullResponse.length() == 0) {
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "AI 未返回内容");
        }

        // 3. 落库 AI 回复
        String aiReply = fullResponse.toString();
        Message aiMsg = new Message();
        aiMsg.setConversationId(conv.getId());
        aiMsg.setSenderId(AI_SENDER_ID);
        aiMsg.setType(MessageTypeEnum.TYPE_TEXT.getCode());
        aiMsg.setContent(aiReply);
        aiMsg.setStatus(0);
        messageMapper.insert(aiMsg);

        // 4. 更新会话上下文 + lastPrompt
        updateContextAndPrompt(conv, dto.getContent(), aiReply);

        return Result.success(aiMsg);
    }

    /**
     * 删除 AI 对话
     */
    @DeleteMapping("/conversations/{conversationId}")
    public Result<Boolean> deleteAiConversation(@PathVariable Long conversationId) {
        Long userId = SecurityUtils.getCurrentUserId();
        AiConversation conv = getOwnedAiConversation(conversationId, userId);
        // 逻辑删除会话
        aiConversationMapper.deleteById(conv.getId());
        // 逻辑删除相关消息
        messageMapper.delete(new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, conv.getId()));
        log.info("[AI] 删除对话: convId={}, userId={}", conv.getId(), userId);
        return Result.success(true);
    }

    // ============================================================
    // 私有辅助方法
    // ============================================================

    /**
     * 限流检查
     */
    private void checkRateLimit(Long userId) {
        String key = RATE_LIMIT_KEY_PREFIX + userId;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            stringRedisTemplate.expire(key, RATE_LIMIT_TTL);
        }
        if (count != null && count > RATE_LIMIT_PER_MINUTE) {
            throw new BusinessException(ResultCode.AI_LIMIT_EXCEEDED);
        }
    }

    /**
     * 从常规会话的历史消息构建 AI 上下文（排除当前刚保存的提问以避免重复）。
     * - senderId=0 → assistant 角色
     * - 其他 senderId → user 角色
     * - 排除系统消息(type=4)
     * - 仅保留最近 MAX_CONTEXT_MESSAGES 条
     */
    private List<Map<String, String>> buildContextFromMessages(Long conversationId, Long userId, String currentPrompt) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (conversationId == null) {
            return messages;
        }
        List<Message> recent = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, conversationId)
                        .ne(Message::getType, MessageTypeEnum.TYPE_SYSTEM.getCode())
                        .orderByDesc(Message::getId)
                        .last("LIMIT " + (MAX_CONTEXT_MESSAGES + 1)));
        // 反转为时间正序
        java.util.Collections.reverse(recent);
        boolean skippedCurrent = false;
        for (Message m : recent) {
            // 跳过当前刚保存的提问（内容与 prompt 一致且为当前用户发送的最新一条），避免上下文重复
            if (!skippedCurrent
                    && currentPrompt != null
                    && currentPrompt.equals(m.getContent())
                    && userId != null && userId.equals(m.getSenderId())) {
                skippedCurrent = true;
                continue;
            }
            Map<String, String> msg = new HashMap<>();
            if (m.getSenderId() != null && m.getSenderId() == AI_SENDER_ID) {
                msg.put("role", "assistant");
            } else {
                msg.put("role", "user");
            }
            msg.put("content", m.getContent());
            messages.add(msg);
        }
        return messages;
    }

    /**
     * 落库 AI 回复到常规会话 + WebSocket 广播给所有成员 + 更新会话最后消息。
     * senderId=0 无 user 记录，手动填充 Vibe助手 昵称与头像。
     */
    private void saveAndBroadcastAiReply(Long conversationId, String reply) {
        if (conversationId == null || StrUtil.isBlank(reply)) {
            return;
        }
        try {
            Message aiMsg = new Message();
            aiMsg.setConversationId(conversationId);
            aiMsg.setSenderId(AI_SENDER_ID);
            aiMsg.setType(MessageTypeEnum.TYPE_TEXT.getCode());
            aiMsg.setContent(reply);
            aiMsg.setStatus(0);
            aiMsg.setSenderName(AI_SENDER_NAME);
            aiMsg.setSenderAvatar(AI_SENDER_AVATAR);
            messageMapper.insert(aiMsg);

            // 更新会话最后一条消息
            conversationMapper.update(null, new LambdaUpdateWrapper<Conversation>()
                    .eq(Conversation::getId, conversationId)
                    .set(Conversation::getLastMessage, reply)
                    .set(Conversation::getLastMessageType, MessageTypeEnum.TYPE_TEXT.getCode())
                    .set(Conversation::getLastMessageAt, java.time.LocalDateTime.now()));

            // WebSocket 推送给会话所有成员
            messagingTemplate.convertAndSend("/topic/conversation." + conversationId, aiMsg);
            log.debug("[AI] WebSocket 推送 AI 回复到会话: {}", conversationId);
        } catch (Exception e) {
            log.warn("[AI] 落库/广播 AI 回复失败: {}", e.getMessage());
        }
    }

    /**
     * 保存/创建 AI 会话 + 落库用户消息(SSE 流式入口)
     *
     * @return 关联的 AI 会话
     */
    private AiConversation saveAiConversation(AiChatDTO dto, Long userId) {
        AiConversation conv;
        if (dto.getConversationId() != null) {
            conv = aiConversationMapper.selectById(dto.getConversationId());
            if (conv == null || !conv.getUserId().equals(userId)) {
                // 会话不存在或不属于当前用户,创建新会话
                conv = createNewAiConversation(userId);
            } else {
                conv.setLastPrompt(dto.getPrompt());
                aiConversationMapper.updateById(conv);
            }
        } else {
            conv = createNewAiConversation(userId);
            conv.setLastPrompt(dto.getPrompt());
            aiConversationMapper.updateById(conv);
        }
        // 落库用户消息
        Message userMsg = new Message();
        userMsg.setConversationId(conv.getId());
        userMsg.setSenderId(userId);
        userMsg.setType(MessageTypeEnum.TYPE_TEXT.getCode());
        userMsg.setContent(dto.getPrompt());
        userMsg.setStatus(0);
        messageMapper.insert(userMsg);
        return conv;
    }

    /**
     * 创建新 AI 会话记录
     */
    private AiConversation createNewAiConversation(Long userId) {
        AiConversation conv = new AiConversation();
        conv.setUserId(userId);
        conv.setTitle("AI 对话");
        conv.setProvider(aiService.getProvider());
        aiConversationMapper.insert(conv);
        return conv;
    }

    /**
     * 落库 AI 回复 + 更新上下文(SSE 流式完成时调用)
     */
    private void saveAiResponseAndUpdateContext(AiConversation conv, String prompt, String reply, Long userId) {
        try {
            if (StrUtil.isBlank(reply)) {
                return;
            }
            // 落库 AI 回复消息
            Message aiMsg = new Message();
            aiMsg.setConversationId(conv.getId());
            aiMsg.setSenderId(AI_SENDER_ID);
            aiMsg.setType(MessageTypeEnum.TYPE_TEXT.getCode());
            aiMsg.setContent(reply);
            aiMsg.setStatus(0);
            messageMapper.insert(aiMsg);
            // 更新上下文
            updateContextAndPrompt(conv, prompt, reply);
        } catch (Exception e) {
            log.warn("[AI] 落库 AI 回复失败: {}", e.getMessage());
        }
    }

    /**
     * 更新会话上下文 + lastPrompt
     * context 存储为 JSON 数组: [{"role":"user","content":"..."},{"role":"assistant","content":"..."}]
     */
    private void updateContextAndPrompt(AiConversation conv, String userContent, String aiContent) {
        try {
            JSONArray contextArr;
            if (StrUtil.isNotBlank(conv.getContext())) {
                contextArr = JSONUtil.parseArray(conv.getContext());
            } else {
                contextArr = new JSONArray();
            }
            JSONObject userTurn = new JSONObject();
            userTurn.set("role", "user");
            userTurn.set("content", userContent);
            contextArr.add(userTurn);
            JSONObject aiTurn = new JSONObject();
            aiTurn.set("role", "assistant");
            aiTurn.set("content", aiContent);
            contextArr.add(aiTurn);
            // 限制上下文长度(保留最近 MAX_CONTEXT_MESSAGES 条)
            while (contextArr.size() > MAX_CONTEXT_MESSAGES) {
                contextArr.remove(0);
            }
            conv.setContext(contextArr.toString());
            conv.setLastPrompt(userContent);
            aiConversationMapper.updateById(conv);
        } catch (Exception e) {
            log.warn("[AI] 更新上下文失败: {}", e.getMessage());
        }
    }

    /**
     * 从 AiConversation.context (JSON) 构建上下文消息列表
     * 格式: [{"role":"user","content":"..."},{"role":"assistant","content":"..."}]
     *
     * @return 可直接传给 AI 服务的消息列表
     */
    private List<Map<String, String>> buildContextMessages(AiConversation conv) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (conv == null || StrUtil.isBlank(conv.getContext())) {
            return messages;
        }
        try {
            JSONArray contextArr = JSONUtil.parseArray(conv.getContext());
            for (Object obj : contextArr) {
                JSONObject turn = (JSONObject) obj;
                Map<String, String> msg = new HashMap<>();
                msg.put("role", turn.getStr("role"));
                msg.put("content", turn.getStr("content"));
                messages.add(msg);
            }
        } catch (Exception e) {
            log.warn("[AI] 解析上下文失败: {}", e.getMessage());
        }
        return messages;
    }

    /**
     * 校验 AI 会话归属并返回
     */
    private AiConversation getOwnedAiConversation(Long conversationId, Long userId) {
        AiConversation conv = aiConversationMapper.selectById(conversationId);
        if (conv == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "AI 对话不存在");
        }
        if (!conv.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作此 AI 对话");
        }
        return conv;
    }

    /**
     * 实体转 VO
     */
    private AiConversationVO toVO(AiConversation conv) {
        AiConversationVO vo = new AiConversationVO();
        BeanUtils.copyProperties(conv, vo);
        return vo;
    }
}
