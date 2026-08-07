package com.chatvibe.module.ai.controller;

import cn.hutool.core.util.StrUtil;
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
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI 对话接口
 * 提供 SSE 流式对话 + REST CRUD 接口
 * <p>
 * 迁移到 Spring AI 后：
 * - 流式输出通过 {@code ChatClient.prompt().stream().content()} 返回 {@link Flux}&lt;String&gt;，桥接到 {@link SseEmitter}
 * - 独立 AI 会话上下文由 ChatMemory（{@code AiConversationChatMemory}）管理，替代手写 context JSON
 *
 * @author Alu
 * @date 2026-07-01
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private static final String DELETE_AI_CONV_LOCK_PREFIX = "ai:delete:conv:";

    private static final String RATE_LIMIT_KEY_PREFIX = "ai:limit:";
    private static final int RATE_LIMIT_PER_MINUTE = 20;
    private static final Duration RATE_LIMIT_TTL = Duration.ofMinutes(1);
    private static final long AI_SENDER_ID = 0L;
    private static final int MAX_CONTEXT_MESSAGES = 20;
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
    private final com.chatvibe.config.AiProviderRegistry aiProviderRegistry;

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
     * <p>
     */
    @PostMapping(value = "/chat", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter chat(@Valid @RequestBody AiChatDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();

        // 限流: 每分钟 20 次
        checkRateLimit(userId);

        // 创建 SseEmitter (超时 3 分钟)
        SseEmitter emitter = new SseEmitter(3 * 60 * 1000L);
        final boolean[] completed = {false};
        final int[] segmentIndex = {0};
        // 段缓冲区：累积 token 直到检测到 \n\n（段落分隔），则拆分为独立消息
        final StringBuilder segmentBuffer = new StringBuilder();

        // 聊天会话ID（私聊/群聊/独立AI会话），用于落库 AI 回复 + WebSocket 广播
        Long chatConvId = dto.getChatConversationId();

        // 构建上下文消息列表（Spring AI Message 形式，从会话历史消息中提取，排除当前提问）
        List<org.springframework.ai.chat.messages.Message> contextMessages =
                buildContextFromMessages(chatConvId, userId, dto.getPrompt());

        // 记录当前使用的供应商和模型到会话（管理员后台可按供应商筛选）
        // chatConvId 是 conversation 表的会话 ID（type=3 AI会话），直接更新该表的 aiProvider/aiModel 字段
        if (chatConvId != null && !aiProviderRegistry.getProviders().isEmpty()) {
            var primary = aiProviderRegistry.getProviders().get(0);
            conversationMapper.update(null, new LambdaUpdateWrapper<Conversation>()
                    .eq(Conversation::getId, chatConvId)
                    .set(Conversation::getAiProvider, primary.name())
                    .set(Conversation::getAiModel, primary.model()));
        }

        // 通过熔断器调用 AI 服务，返回 Flux<String>，订阅后桥接到 SseEmitter
        // onErrorResume 确保熔断器未触发降级时（如失败次数未达阈值），
        // 错误在 Reactor 操作符层面被捕获并替换为兜底 Flux，
        // 不会传播到 Servlet 线程触发 "response already committed" 异常
        Flux<String> tokenFlux = aiCiruitBreakerService.chatStreamWithCircuitBreaker(dto.getPrompt(), contextMessages)
                .onErrorResume(error -> {
                    log.warn("[AI] 主服务异常，手动降级到兜底: {}", error.getMessage());
                    return aiFallbackService.chatStream(dto.getPrompt(), contextMessages);
                });

        tokenFlux.subscribe(
                token -> {
                    try {
                        if (completed[0]) {
                            return;
                        }
                        // 1. 累积到段缓冲区（原始 token，含可能的 Markdown 符号）
                        segmentBuffer.append(token);

                        // 2. 检测段落分隔符 \n\n → 拆分为独立消息
                        //    循环处理：单个 token 可能包含多个 \n\n
                        while (true) {
                            String bufferStr = segmentBuffer.toString();
                            int splitIdx = bufferStr.indexOf("\n\n");
                            if (splitIdx == -1) {
                                break;
                            }
                            String segment = bufferStr.substring(0, splitIdx).trim();
                            String remainder = bufferStr.substring(splitIdx + 2);
                            segmentBuffer.setLength(0);
                            segmentBuffer.append(remainder);

                            if (!segment.isEmpty()) {
                                // 清洗 Markdown 语法（**加粗**、# 标题、`代码` 等）
                                String cleaned = stripMarkdown(segment);
                                // 通知前端：用清洗后的内容替换当前消息段（去掉流式累积的 ** 等符号）
                                emitter.send(SseEmitter.event().name("segment").data(cleaned));
                                segmentIndex[0]++;
                                // 落库 + WebSocket 广播此段（已清洗）
                                saveAndBroadcastAiReply(chatConvId, cleaned);
                            }
                        }

                        // 3. 发送当前段落的清洗内容（replace 事件，全量替换而非追加）
                        //    前端收到后直接设置 msg.content = cleaned，不追加原始 token
                        //    这样流式过程中用户看到的始终是纯文本，不会闪现 ** # ` 等 Markdown 符号
                        //    仅在缓冲区有内容时发送，避免向 segment 创建的空占位发送空 replace
                        String cleanedCurrent = stripMarkdown(segmentBuffer.toString());
                        if (!cleanedCurrent.isEmpty()) {
                            emitter.send(SseEmitter.event().name("replace").data(cleanedCurrent));
                        }
                    } catch (IOException e) {
                        log.warn("[AI] SseEmitter 发送失败: {}", e.getMessage());
                        emitter.completeWithError(e);
                    }
                },
                error -> {
                    // onErrorResume 已将主服务错误转为兜底 Flux，
                    // 此处仅当兜底 Flux 自身也失败时才会触发（极端情况）
                    log.error("[AI] 兜底服务也失败: {}", error.getMessage());
                    if (!completed[0]) {
                        completed[0] = true;
                        try {
                            emitter.send(SseEmitter.event().name("error").data("AI 服务不可用"));
                            emitter.complete();
                        } catch (IOException ignored) {
                            emitter.completeWithError(error);
                        }
                    }
                },
                () -> {
                    if (completed[0]) {
                        return;
                    }
                    completed[0] = true;
                    try {
                        // 保存最后一段（如果有剩余内容）
                        // 最后一段内容已通过 replace 事件展示在前端，此处只需落库+广播
                        String lastSegment = segmentBuffer.toString().trim();
                        if (!lastSegment.isEmpty()) {
                            String cleaned = stripMarkdown(lastSegment);
                            saveAndBroadcastAiReply(chatConvId, cleaned);
                            segmentIndex[0]++;
                        }
                        emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                        emitter.complete();
                        log.info("[AI] 对话完成: userId={}, convId={}, segments={}",
                                userId, chatConvId, segmentIndex[0]);
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                }
        );

        // 超时和错误处理
        emitter.onTimeout(() -> {
            completed[0] = true;
            log.warn("[AI] SseEmitter 超时: userId={}", userId);
        });
        emitter.onError(e -> {
            completed[0] = true;
            log.warn("[AI] SseEmitter 异常: userId={}, msg={}", userId, e.getMessage());
        });

        return emitter;
    }

    // ============================================================
    // REST CRUD 接口
    // ============================================================

    /**
     * 获取 AI 对话列表
     */
    @GetMapping("/conversations")
    @RateLimiter(name = "aiConvListRateLimiter", fallbackMethod = "getAiConversationsFallback")
    @CircuitBreaker(name = "aiConvListService", fallbackMethod = "getAiConversationsFallback")
    public Result<List<AiConversationVO>> getAiConversations() {
        Long userId = SecurityUtils.getCurrentUserId();
        List<AiConversation> list = aiConversationMapper.selectList(
                new LambdaQueryWrapper<AiConversation>()
                        .eq(AiConversation::getUserId, userId)
                        .orderByDesc(AiConversation::getUpdatedAt)
                        .orderByDesc(AiConversation::getId)
                        .last("LIMIT 100"));
        return Result.success(list.stream().map(this::toVO).collect(Collectors.toList()));
    }

    /**
     * 创建新的 AI 对话
     */
    @PostMapping("/conversations")
    @RateLimiter(name = "aiCreateConvRateLimiter", fallbackMethod = "createAiConversationFallback")
    @CircuitBreaker(name = "aiCreateConvService", fallbackMethod = "createAiConversationFallback")
    public Result<AiConversationVO> createAiConversation(@RequestBody(required = false) CreateAiConversationDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        AiConversation conv = new AiConversation();
        conv.setUserId(userId);
        conv.setTitle(StrUtil.isNotBlank(dto == null ? null : dto.getTitle()) ? dto.getTitle() : "AI 对话");
        // 记录创建时的供应商和模型（管理员后台可按供应商筛选）
        if (!aiProviderRegistry.getProviders().isEmpty()) {
            var primary = aiProviderRegistry.getProviders().get(0);
            conv.setProvider(primary.name());
            conv.setModel(primary.model());
        } else {
            conv.setProvider(aiService.getProvider());
        }
        aiConversationMapper.insert(conv);
        log.info("[AI] 创建新对话: convId={}, userId={}", conv.getId(), userId);
        return Result.success(toVO(conv));
    }

    /**
     * 获取 AI 对话历史消息
     */
    @GetMapping("/conversations/{conversationId}/messages")
    @RateLimiter(name = "aiMessagesRateLimiter", fallbackMethod = "getAiMessagesFallback")
    @CircuitBreaker(name = "aiMessagesService", fallbackMethod = "getAiMessagesFallback")
    public Result<List<Message>> getAiMessages(@PathVariable Long conversationId) {
        Long userId = SecurityUtils.getCurrentUserId();
        AiConversation conv = getOwnedAiConversation(conversationId, userId);
        List<Message> messages = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, conv.getId())
                        .orderByAsc(Message::getId)
                        .last("LIMIT 200"));
        return Result.success(messages);
    }

    /**
     * 发送 AI 对话请求(非流式,作为兜底)
     * 保存用户消息 → 调用 AI → 保存 AI 回复 → 返回 AI 回复消息
     * <p>
     */
    @PostMapping("/conversations/{conversationId}/messages")
    @RateLimiter(name = "aiSendMsgRateLimiter", fallbackMethod = "sendAiMessageFallback")
    @CircuitBreaker(name = "aiSendMsgService", fallbackMethod = "sendAiMessageFallback")
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

        // 2. 通过 ChatMemory 加载上下文（Spring AI Message 形式）
        List<org.springframework.ai.chat.messages.Message> context =
                chatMemory.get(String.valueOf(conv.getId()));

        // 3. 调用主 AI 服务（同步收集完整回复）
        StringBuilder fullResponse = new StringBuilder();
        Flux<String> mainFlux = aiService.chatStream(dto.getContent(), context);
        try {
            mainFlux.doOnNext(fullResponse::append)
                    .blockLast();
        } catch (Exception e) {
            log.warn("[AI] 非流式调用主服务失败，切换兜底: {}", e.getMessage());
        }

        // 主服务无输出时走兜底
        if (fullResponse.length() == 0) {
            fullResponse.setLength(0);
            try {
                aiFallbackService.chatStream(dto.getContent(), context)
                        .doOnNext(fullResponse::append)
                        .blockLast();
            } catch (Exception e) {
                log.warn("[AI] 兜底调用失败: {}", e.getMessage());
            }
        }

        if (fullResponse.length() == 0) {
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "AI 未返回内容");
        }

        // 4. 落库 AI 回复（清洗 Markdown 语法）
        String aiReply = stripMarkdown(fullResponse.toString());
        Message aiMsg = new Message();
        aiMsg.setConversationId(conv.getId());
        aiMsg.setSenderId(AI_SENDER_ID);
        aiMsg.setType(MessageTypeEnum.TYPE_TEXT.getCode());
        aiMsg.setContent(aiReply);
        aiMsg.setStatus(0);
        messageMapper.insert(aiMsg);

        // 5. 通过 ChatMemory 回写本轮对话（自动维护上下文窗口）
        chatMemory.add(String.valueOf(conv.getId()), List.of(
                new UserMessage(dto.getContent()),
                new AssistantMessage(aiReply)
        ));
        // 同步 lastPrompt + 记录当前使用的供应商和模型（管理员后台可按供应商筛选）
        conv.setLastPrompt(dto.getContent());
        if (!aiProviderRegistry.getProviders().isEmpty()) {
            var primary = aiProviderRegistry.getProviders().get(0);
            conv.setProvider(primary.name());
            conv.setModel(primary.model());
        }
        aiConversationMapper.updateById(conv);

        return Result.success(aiMsg);
    }

    /**
     * 删除 AI 对话
     */
    @DeleteMapping("/conversations/{conversationId}")
    @RateLimiter(name = "aiDeleteConvRateLimiter", fallbackMethod = "deleteAiConversationFallback")
    @CircuitBreaker(name = "aiDeleteConvService", fallbackMethod = "deleteAiConversationFallback")
    @Transactional(rollbackFor = Exception.class)
    public Result<Boolean> deleteAiConversation(@PathVariable Long conversationId) {
        Long userId = SecurityUtils.getCurrentUserId();
        AiConversation conv = getOwnedAiConversation(conversationId, userId);

        // Redis 分布式锁防止并发删除
        String lockKey = DELETE_AI_CONV_LOCK_PREFIX + userId + ":" + conversationId;
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", Duration.ofSeconds(3));
        if (!Boolean.TRUE.equals(locked)) {
            throw new BusinessException(ResultCode.FAIL, "操作过于频繁，请稍后再试");
        }

        aiConversationMapper.deleteById(conv.getId());
        messageMapper.delete(new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, conv.getId()));
        // 清空 ChatMemory 上下文
        chatMemory.clear(String.valueOf(conv.getId()));
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
     * 从常规会话的历史消息构建 AI 上下文（Spring AI Message 形式，排除当前刚保存的提问以避免重复）。
     * - senderId=0 → assistant 角色
     * - 其他 senderId → user 角色
     * - 排除系统消息(type=4)
     * - 仅保留最近 MAX_CONTEXT_MESSAGES 条
     */
    private List<org.springframework.ai.chat.messages.Message> buildContextFromMessages(
            Long conversationId, Long userId, String currentPrompt) {
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
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
        Collections.reverse(recent);
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
            if (m.getSenderId() != null && m.getSenderId() == AI_SENDER_ID) {
                messages.add(new AssistantMessage(m.getContent()));
            } else {
                messages.add(new UserMessage(m.getContent()));
            }
        }
        return messages;
    }

    /**
     * 清洗 Markdown 语法符号，将 AI 回复转为纯自然语言。
     * <p>
     * 采用"先匹配完整模式，再清除残留符号"两遍策略：
     * 1. 先用正则匹配完整的 Markdown 模式（如 **text**）并提取内容
     * 2. 再用字符串替换清除所有残留的 Markdown 符号（如未闭合的 ** * ` _ #）
     * <p>
     * 这样无论 AI 输出是否成对使用 Markdown 符号，最终结果都是纯文本。
     * 流式输出中也使用此方法，确保 token 累积过程中不会闪现 ** # 等符号。
     *
     * @param text 可能包含 Markdown 语法的文本
     * @return 纯文本，所有 Markdown 符号已剥离
     */
    private String stripMarkdown(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = text;
        // --- 第一遍：匹配完整模式 ---
        // 1. 代码块: ```lang\ntext\n``` → text
        result = result.replaceAll("```[^\\n]*\\n([\\s\\S]*?)```", "$1");
        // 2. 行内代码: `text` → text
        result = result.replaceAll("`([^`]+)`", "$1");
        // 3. 加粗: **text** → text
        result = result.replaceAll("\\*\\*(.+?)\\*\\*", "$1");
        // 4. 斜体: *text* → text（** 已处理，剩余 * 为斜体）
        result = result.replaceAll("(?<!\\*)\\*(?!\\*)([^*\\n]+?)(?<!\\*)\\*(?!\\*)", "$1");
        // 5. 下划线加粗: __text__ → text
        result = result.replaceAll("__(.+?)__", "$1");
        // 6. 下划线斜体: _text_ → text
        result = result.replaceAll("(?<!_)_(?!_)([^_\\n]+?)(?<!_)_(?!_)", "$1");
        // 7. 链接: [text](url) → text
        result = result.replaceAll("\\[([^]]+)]\\([^)]+\\)", "$1");
        // 8. 图片: ![alt](url) → alt
        result = result.replaceAll("!\\[([^]]+)]\\([^)]+\\)", "$1");
        // 9. 标题: # text → text
        result = result.replaceAll("(?m)^#{1,6}\\s+", "");
        // 10. 无序列表: - item / * item / + item → item
        result = result.replaceAll("(?m)^[-*+]\\s+", "");
        // 11. 有序列表: 1. item → item
        result = result.replaceAll("(?m)^\\d+\\.\\s+", "");
        // 12. 引用: > text → text
        result = result.replaceAll("(?m)^>\\s?", "");
        // 13. 分隔线: --- / *** / ___ → 移除
        result = result.replaceAll("(?m)^[-*_=]{3,}$", "");

        // --- 第二遍：清除残留符号（处理未闭合/不完整的 Markdown） ---
        // 这些符号在 IM 聊天场景下不可能作为正常文本出现
        result = result.replace("**", "");   // 残留的加粗标记
        result = result.replace("`", "");     // 残留的代码标记
        result = result.replace("*", "");     // 残留的斜体标记
        result = result.replace("__", "");    // 残留的下划线加粗标记
        result = result.replace("_", "");     // 残留的下划线斜体标记
        result = result.replaceAll("(?m)^#+\\s*$", ""); // 行首残留的 #

        // 14. 清理多余空格
        result = result.replaceAll("  +", " ");
        return result.trim();
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

    /** AI对话列表降级 */
    private Result<List<AiConversationVO>> getAiConversationsFallback(Throwable t) {
        if (t instanceof RequestNotPermitted) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS);
        }
        log.warn("[AI] 对话列表熔断降级: err={}", t.getMessage());
        return Result.success(Collections.emptyList());
    }

    /** 创建AI对话降级 */
    private Result<AiConversationVO> createAiConversationFallback(CreateAiConversationDTO dto, Throwable t) {
        if (t instanceof RequestNotPermitted) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS);
        }
        if (t instanceof BusinessException) {
            throw (BusinessException) t;
        }
        log.warn("[AI] 创建对话熔断降级: err={}", t.getMessage());
        return Result.success(null);
    }

    /** AI历史消息降级 */
    private Result<List<Message>> getAiMessagesFallback(Long conversationId, Throwable t) {
        if (t instanceof RequestNotPermitted) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS);
        }
        if (t instanceof BusinessException) {
            throw (BusinessException) t;
        }
        log.warn("[AI] 历史消息熔断降级: convId={}, err={}", conversationId, t.getMessage());
        return Result.success(Collections.emptyList());
    }

    /** 非流式AI对话降级 */
    private Result<Message> sendAiMessageFallback(Long conversationId, SendAiMessageDTO dto, Throwable t) {
        if (t instanceof RequestNotPermitted) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS);
        }
        if (t instanceof BusinessException) {
            throw (BusinessException) t;
        }
        log.warn("[AI] 非流式对话熔断降级: convId={}, err={}", conversationId, t.getMessage());
        return Result.success(null);
    }

    /** 删除AI对话降级 */
    private Result<Boolean> deleteAiConversationFallback(Long conversationId, Throwable t) {
        if (t instanceof RequestNotPermitted) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS);
        }
        if (t instanceof BusinessException) {
            throw (BusinessException) t;
        }
        log.warn("[AI] 删除对话熔断降级: convId={}, err={}", conversationId, t.getMessage());
        return Result.success(false);
    }

    // ============================================================
    // 依赖注入：ChatMemory（由 AiConversationChatMemory 提供）
    // ============================================================
    private final org.springframework.ai.chat.memory.ChatMemory chatMemory;
}
