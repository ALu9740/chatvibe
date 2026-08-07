package com.chatvibe.module.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chatvibe.common.result.PageResult;
import com.chatvibe.module.admin.enums.OperationTypeEnum;
import com.chatvibe.module.admin.service.AdminLogService;
import com.chatvibe.module.admin.service.AdminMessageService;
import com.chatvibe.module.admin.vo.AuditMessageVO;
import com.chatvibe.module.chat.entity.Conversation;
import com.chatvibe.module.chat.entity.Message;
import com.chatvibe.module.chat.entity.MessageHidden;
import com.chatvibe.module.chat.mapper.ConversationMapper;
import com.chatvibe.module.chat.mapper.MessageHiddenMapper;
import com.chatvibe.module.chat.mapper.MessageMapper;
import com.chatvibe.module.user.entity.User;
import com.chatvibe.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理员消息审计服务实现
 *
 * @author Alu
 * @date 2026-08-07
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminMessageServiceImpl implements AdminMessageService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MessageMapper messageMapper;
    private final ConversationMapper conversationMapper;
    private final UserMapper userMapper;
    private final MessageHiddenMapper messageHiddenMapper;
    private final AdminLogService adminLogService;

    @Override
    public PageResult<AuditMessageVO> searchMessages(String keyword, Long senderId, Long conversationId, String type, String startDate, String endDate, int page, int size) {
        size = Math.min(size, 50);

        // 解析筛选条件
        String trimmedKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        LocalDateTime startTime = (startDate != null && !startDate.trim().isEmpty())
                ? LocalDate.parse(startDate.trim()).atStartOfDay() : null;
        LocalDateTime endTime = (endDate != null && !endDate.trim().isEmpty())
                ? LocalDate.parse(endDate.trim()).atTime(LocalTime.MAX) : null;

        // AI 类型特殊处理: senderId=0 且 type=0(文本)
        boolean aiOnly = false;
        Integer typeInt = null;
        if (type != null && !type.trim().isEmpty()) {
            String typeUpper = type.trim().toUpperCase();
            if ("AI".equals(typeUpper)) {
                aiOnly = true;
            } else {
                typeInt = mapTypeToInt(typeUpper);
            }
        }

        // 使用自定义查询（含已删除记录，绕过 MyBatis-Plus 逻辑删除过滤）
        int offset = (page - 1) * size;
        List<Message> messages = messageMapper.selectAuditMessagesPage(
                trimmedKeyword, senderId, conversationId, typeInt, aiOnly, startTime, endTime, offset, size);
        long total = messageMapper.countAuditMessages(
                trimmedKeyword, senderId, conversationId, typeInt, aiOnly, startTime, endTime);

        if (messages.isEmpty()) {
            return PageResult.of(total, (long) page, (long) size, Collections.emptyList());
        }

        // 批量查询会话信息
        Set<Long> conversationIds = messages.stream()
                .map(Message::getConversationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Conversation> conversationMap = batchGetConversations(conversationIds);

        // 批量查询发送者信息
        Set<Long> senderIds = messages.stream()
                .map(Message::getSenderId)
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = batchGetUsers(senderIds);

        // 批量查询被用户隐藏(删除)的消息ID
        Set<Long> messageIds = messages.stream()
                .map(Message::getId)
                .collect(Collectors.toSet());
        Set<Long> hiddenMessageIds = batchGetHiddenMessageIds(messageIds);

        List<AuditMessageVO> records = messages.stream()
                .map(m -> toVO(m, conversationMap, userMap, hiddenMessageIds))
                .collect(Collectors.toList());

        return PageResult.of(total, (long) page, (long) size, records);
    }

    /**
     * Message -> AuditMessageVO
     */
    private AuditMessageVO toVO(Message message, Map<Long, Conversation> conversationMap,
                                Map<Long, User> userMap, Set<Long> hiddenMessageIds) {
        AuditMessageVO vo = new AuditMessageVO();
        vo.setId(message.getId());
        vo.setConversationId(message.getConversationId());
        vo.setSenderId(message.getSenderId());
        vo.setContent(message.getContent());
        vo.setExtra(message.getExtra());
        vo.setType(resolveMessageType(message));
        vo.setDeleted(message.getDeleted() != null && message.getDeleted() == 1);
        vo.setHidden(hiddenMessageIds.contains(message.getId()));
        if (message.getCreatedAt() != null) {
            vo.setCreatedAt(message.getCreatedAt().format(DATE_TIME_FORMATTER));
        }

        Conversation conversation = message.getConversationId() == null
                ? null : conversationMap.get(message.getConversationId());
        if (conversation != null) {
            vo.setConversationName(conversation.getName());
            vo.setConversationType(mapConversationType(conversation.getType()));
        }

        // AI 消息(senderId=0, 非系统)的发送者名称
        if (message.getSenderId() != null && message.getSenderId() == 0) {
            if (message.getType() != null && message.getType() == 4) {
                vo.setSenderName("系统");
            } else {
                vo.setSenderName("Vibe助手");
            }
        } else {
            User sender = message.getSenderId() == null ? null : userMap.get(message.getSenderId());
            if (sender != null) {
                vo.setSenderName(sender.getNickname());
            }
        }
        return vo;
    }

    /**
     * 解析消息类型: AI 消息(senderId=0 且 type=0)返回 "AI"，其余按 type 映射
     */
    private String resolveMessageType(Message message) {
        Integer type = message.getType();
        Long senderId = message.getSenderId();
        // AI 消息: senderId=0 且非系统消息
        if (senderId != null && senderId == 0 && (type == null || type == 0)) {
            return "AI";
        }
        return mapTypeToString(type);
    }

    /**
     * 批量查询会话
     */
    private Map<Long, Conversation> batchGetConversations(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Conversation> conversations = conversationMapper.selectList(
                new LambdaQueryWrapper<Conversation>().in(Conversation::getId, ids));
        return conversations.stream().collect(Collectors.toMap(Conversation::getId, c -> c, (a, b) -> a));
    }

    /**
     * 批量查询用户
     */
    private Map<Long, User> batchGetUsers(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>().in(User::getId, ids));
        return users.stream().collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
    }

    /**
     * 批量查询被用户隐藏的消息ID
     */
    private Set<Long> batchGetHiddenMessageIds(Collection<Long> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<MessageHidden> hiddens = messageHiddenMapper.selectList(
                new LambdaQueryWrapper<MessageHidden>().in(MessageHidden::getMessageId, messageIds));
        return hiddens.stream().map(MessageHidden::getMessageId).collect(Collectors.toSet());
    }

    /**
     * 消息类型字符串 -> 数据库 int
     * TEXT=0, IMAGE=1, FILE=3, SYSTEM=4
     * AI 不在此映射，由 aiOnly 参数单独处理
     */
    private Integer mapTypeToInt(String type) {
        return switch (type) {
            case "TEXT" -> 0;
            case "IMAGE" -> 1;
            case "FILE" -> 3;
            case "SYSTEM" -> 4;
            default -> null;
        };
    }

    /**
     * 数据库 int -> 消息类型字符串
     * 0->TEXT, 1->IMAGE, 3->FILE, 4->SYSTEM, 其他->TEXT
     */
    private String mapTypeToString(Integer type) {
        if (type == null) {
            return "TEXT";
        }
        return switch (type) {
            case 0 -> "TEXT";
            case 1 -> "IMAGE";
            case 3 -> "FILE";
            case 4 -> "SYSTEM";
            default -> "TEXT";
        };
    }

    /**
     * 会话类型 int -> 中文
     * 1->私聊, 2->群聊, 3->AI
     */
    private String mapConversationType(Integer type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case 1 -> "私聊";
            case 2 -> "群聊";
            case 3 -> "AI";
            default -> null;
        };
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteMessage(Long messageId, String reason) {
        // 逻辑删除: MyBatis-Plus @TableLogic 会将 deleted 字段置为 1
        // C端查询会过滤 deleted=0, 所以用户不可见; 管理员审计查询含已删除记录, 仍可查看
        messageMapper.deleteById(messageId);
        adminLogService.log(OperationTypeEnum.MESSAGE_DELETE, "删除消息ID: " + messageId + ", 原因: " + reason);
        log.info("[管理员] 删除消息成功: messageId={}, 原因={}", messageId, reason);
        return true;
    }
}
