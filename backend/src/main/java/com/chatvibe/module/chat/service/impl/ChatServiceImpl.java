package com.chatvibe.module.chat.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chatvibe.common.result.ResultCode;
import com.chatvibe.common.exception.BusinessException;
import com.chatvibe.module.chat.dto.SendMessageDTO;
import com.chatvibe.module.chat.entity.Conversation;
import com.chatvibe.module.chat.entity.ConversationMember;
import com.chatvibe.module.chat.entity.Message;
import com.chatvibe.module.chat.entity.MessageHidden;
import com.chatvibe.module.chat.enums.*;
import com.chatvibe.module.chat.mapper.ConversationMapper;
import com.chatvibe.module.chat.mapper.ConversationMemberMapper;
import com.chatvibe.module.chat.mapper.MessageHiddenMapper;
import com.chatvibe.module.chat.mapper.MessageMapper;
import com.chatvibe.module.chat.service.ChatService;
import com.chatvibe.module.chat.vo.ConversationVO;
import com.chatvibe.module.group.entity.GroupMember;
import com.chatvibe.module.group.mapper.GroupMemberMapper;
import com.chatvibe.module.user.entity.User;
import com.chatvibe.module.user.service.UserService;
import com.chatvibe.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 聊天服务实现
 *
 * @author Alu
 * @date 2026-06-30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ConversationMapper conversationMapper;
    private final ConversationMemberMapper conversationMemberMapper;
    private final MessageMapper messageMapper;
    private final MessageHiddenMapper messageHiddenMapper;
    private final GroupMemberMapper groupMemberMapper;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public List<ConversationVO> getConversationList() {
        Long userId = SecurityUtils.getCurrentUserId();
        return conversationMapper.selectConversationsByUserId(userId);
    }

    @Override
    public List<Message> getHistoryMessages(Long conversationId, Long lastId, int size) {
        Long userId = SecurityUtils.getCurrentUserId();
        // 校验是否为会话成员
        if (!isMember(conversationId, userId)) {
            throw new BusinessException(ResultCode.NOT_CONVERSATION_MEMBER);
        }
        if (size <= 0 || size > 100) {
            size = 20;
        }
        return messageMapper.selectMessagesPage(conversationId, userId, lastId, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Message sendMessage(SendMessageDTO dto) {
        return sendMessage(dto, SecurityUtils.getCurrentUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Message sendMessage(SendMessageDTO dto, Long senderId) {
        Long userId = senderId;
        if (!isMember(dto.getConversationId(), userId)) {
            throw new BusinessException(ResultCode.NOT_CONVERSATION_MEMBER);
        }
        // 群组已解散则禁止发送消息
        Conversation conv = conversationMapper.selectById(dto.getConversationId());
        if (conv != null && conv.getDissolved() != null && conv.getDissolved() == 1) {
            throw new BusinessException(ResultCode.FORBIDDEN, "此群组已被解散");
        }

        // 落库消息
        Message message = new Message();
        message.setConversationId(dto.getConversationId());
        message.setSenderId(userId);
        message.setType(dto.getType() == null ? MessageTypeEnum.TYPE_TEXT.getCode() : dto.getType());
        message.setContent(dto.getContent());
        message.setExtra(dto.getExtra());
        message.setStatus(0);
        messageMapper.insert(message);

        // 更新会话最后消息（图片/文件转为预览文本，便于会话列表展示）
        int msgType = message.getType();
        String lastMsg = buildLastMessagePreview(msgType, dto.getContent(), dto.getExtra());
        conversationMapper.update(null, new LambdaUpdateWrapper<Conversation>()
                .eq(Conversation::getId, dto.getConversationId())
                .set(Conversation::getLastMessage, lastMsg)
                .set(Conversation::getLastMessageType, msgType)
                .set(Conversation::getLastMessageAt, LocalDateTime.now()));

        // 恢复已删除会话的成员记录：有新消息时，会话重新出现在他们的列表中，
        // 且 created_at 重置为当前时间，使他们只能看到新消息及之后的消息（旧消息被隐藏）。
        if (conv != null && conv.getType() != null && conv.getType() == ConversationTypeEnum.TYPE_GROUP.getCode()) {
            conversationMemberMapper.restoreDeletedGroupMembers(dto.getConversationId(), userId);
        } else {
            conversationMemberMapper.restoreDeletedMembers(dto.getConversationId(), userId);
        }

        // 原子更新其他成员未读数(单条 SQL,避免并发问题)
        conversationMemberMapper.update(null, new LambdaUpdateWrapper<ConversationMember>()
                .eq(ConversationMember::getConversationId, dto.getConversationId())
                .ne(ConversationMember::getUserId, userId)
                .setSql("unread_count = unread_count + 1"));

        // 填充发送者昵称和头像（用于群聊展示），再通过 WebSocket 推送
        User sender = userService.getById(userId);
        if (sender != null) {
            message.setSenderName(sender.getNickname());
            message.setSenderAvatar(sender.getAvatar());
        }

        // WebSocket 推送
        try {
            messagingTemplate.convertAndSend("/topic/conversation." + dto.getConversationId(), message);
            log.debug("[WebSocket] 推送消息到会话: {}", dto.getConversationId());
        } catch (Exception e) {
            log.warn("[WebSocket] 消息推送失败: {}", e.getMessage());
        }

        return message;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAsRead(Long conversationId) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (!isMember(conversationId, userId)) {
            throw new BusinessException(ResultCode.NOT_CONVERSATION_MEMBER);
        }
        conversationMemberMapper.update(null, new LambdaUpdateWrapper<ConversationMember>()
                .eq(ConversationMember::getConversationId, conversationId)
                .eq(ConversationMember::getUserId, userId)
                .set(ConversationMember::getUnreadCount, 0)
                .set(ConversationMember::getLastReadAt, LocalDateTime.now()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleMute(Long conversationId) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (!isMember(conversationId, userId)) {
            throw new BusinessException(ResultCode.NOT_CONVERSATION_MEMBER);
        }
        ConversationMember cm = conversationMemberMapper.selectOne(new LambdaQueryWrapper<ConversationMember>()
                .eq(ConversationMember::getConversationId, conversationId)
                .eq(ConversationMember::getUserId, userId));
        int newMuted = (cm != null && cm.getMuted() != null && cm.getMuted() == ConversationMemberMutedEnum.MUTE_YES.getCode()) ? ConversationMemberMutedEnum.MUTE_NO.getCode() : ConversationMemberMutedEnum.MUTE_YES.getCode();
        conversationMemberMapper.update(null, new LambdaUpdateWrapper<ConversationMember>()
                .eq(ConversationMember::getConversationId, conversationId)
                .eq(ConversationMember::getUserId, userId)
                .set(ConversationMember::getMuted, newMuted));
        return newMuted == ConversationMemberMutedEnum.MUTE_YES.getCode();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean togglePin(Long conversationId) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (!isMember(conversationId, userId)) {
            throw new BusinessException(ResultCode.NOT_CONVERSATION_MEMBER);
        }
        ConversationMember cm = conversationMemberMapper.selectOne(new LambdaQueryWrapper<ConversationMember>()
                .eq(ConversationMember::getConversationId, conversationId)
                .eq(ConversationMember::getUserId, userId));
        int newPinned = (cm != null && cm.getPinned() != null && cm.getPinned() == ConversationMemberPinnedEnum.PINNED_YES.getCode()) ? ConversationMemberPinnedEnum.PINNED_NO.getCode() : ConversationMemberPinnedEnum.PINNED_YES.getCode();
        conversationMemberMapper.update(null, new LambdaUpdateWrapper<ConversationMember>()
                .eq(ConversationMember::getConversationId, conversationId)
                .eq(ConversationMember::getUserId, userId)
                .set(ConversationMember::getPinned, newPinned));
        return newPinned == ConversationMemberPinnedEnum.PINNED_YES.getCode();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Conversation createPrivateConversation(Long userA, Long userB) {
        // 查询是否已有私聊会话（双方成员均未删除）
        Conversation exist = conversationMapper.selectPrivateConversation(userA, userB);
        if (exist != null) {
            return exist;
        }
        // 查询是否存在已删除成员记录的私聊会话（一方删除了会话）
        Conversation existAny = conversationMapper.selectPrivateConversationAnyStatus(userA, userB);
        if (existAny != null) {
            // 仅恢复当前用户（userA）的成员记录：取消逻辑删除 + 重置加入时间为当前时间，
            // 使 userA 无法看到删除前的历史消息。
            // 不恢复 userB：若 userB 未删除会话则其记录不受影响（保留完整聊天记录）；
            // 若 userB 也删除了会话，则 userB 需自行通过好友管理恢复，届时才重置其加入时间。
            conversationMemberMapper.restoreMember(existAny.getId(), userA);
            log.info("[会话] 恢复私聊会话成员: convId={}, userA={}", existAny.getId(), userA);
            return existAny;
        }
        // 创建会话
        Conversation conversation = new Conversation();
        conversation.setType(ConversationTypeEnum.TYPE_PRIVATE.getCode());
        conversation.setMemberCount(2);
        conversation.setLastMessageAt(LocalDateTime.now());
        conversationMapper.insert(conversation);

        // 添加成员
        addMember(conversation.getId(), userA, ConversationMemberRoleEnum.ROLE_MEMBER.getCode());
        addMember(conversation.getId(), userB, ConversationMemberRoleEnum.ROLE_MEMBER.getCode());
        log.info("[会话] 创建私聊会话: convId={}, userA={}, userB={}", conversation.getId(), userA, userB);
        return conversation;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Conversation createAiConversation(Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        Conversation conversation = new Conversation();
        conversation.setType(ConversationTypeEnum.TYPE_AI.getCode());
        //TODO: AI 默认名称
        conversation.setName("Vibe助手");
        //TODO:AI 默认头像
        conversation.setAvatar("🤖");
        conversation.setOwnerId(userId);
        conversation.setMemberCount(1);
        conversation.setLastMessageAt(LocalDateTime.now());
        conversationMapper.insert(conversation);

        // AI 会话只有用户自己一个成员
        addMember(conversation.getId(), userId, ConversationMemberRoleEnum.ROLE_OWNER.getCode());
        log.info("[会话] 创建AI会话: convId={}, userId={}", conversation.getId(), userId);
        return conversation;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Conversation createGroupConversation(Long ownerId, String name, List<Long> memberIds) {
        Conversation conversation = new Conversation();
        conversation.setType(ConversationTypeEnum.TYPE_GROUP.getCode());
        conversation.setName(name);
        conversation.setOwnerId(ownerId);
        conversation.setLastMessageAt(LocalDateTime.now());

        // 成员列表包含群主
        List<Long> allMembers = new ArrayList<>();
        if (!allMembers.add(ownerId)) {
            // ignore
        }
        if (CollUtil.isNotEmpty(memberIds)) {
            for (Long mid : memberIds) {
                if (!allMembers.contains(mid)) {
                    allMembers.add(mid);
                }
            }
        }
        conversation.setMemberCount(allMembers.size());
        conversationMapper.insert(conversation);

        // 添加群主
        addMember(conversation.getId(), ownerId, ConversationMemberRoleEnum.ROLE_OWNER.getCode());
        // 添加其他成员
        for (Long mid : allMembers) {
            if (!mid.equals(ownerId)) {
                addMember(conversation.getId(), mid, ConversationMemberRoleEnum.ROLE_MEMBER.getCode());
            }
        }
        log.info("[会话] 创建群聊会话: convId={}, name={}, memberCount={}",
                conversation.getId(), name, allMembers.size());
        return conversation;
    }

    @Override
    public boolean isMember(Long conversationId, Long userId) {
        Long count = conversationMemberMapper.selectCount(
                new LambdaQueryWrapper<ConversationMember>()
                        .eq(ConversationMember::getConversationId, conversationId)
                        .eq(ConversationMember::getUserId, userId));
        return count > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConversationVO createOrGetPrivateConversation(Long targetUserId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (targetUserId.equals(currentUserId)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "不能与自己创建私聊会话");
        }
        // 校验目标用户存在
        User targetUser = userService.getById(targetUserId);
        if (targetUser == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        Conversation conversation = createPrivateConversation(currentUserId, targetUserId);
        // 转换为 VO（私聊会话的 name/avatar 从对方用户动态获取，conversation 表不存储）
        ConversationVO vo = new ConversationVO();
        vo.setId(conversation.getId());
        vo.setName(targetUser.getNickname());
        vo.setType(conversation.getType());
        vo.setAvatar(targetUser.getAvatar());
        vo.setOwnerId(conversation.getOwnerId());
        vo.setLastMessage(conversation.getLastMessage());
        vo.setLastMessageAt(conversation.getLastMessageAt());
        vo.setMemberCount(conversation.getMemberCount());
        vo.setUnreadCount(0);
        vo.setCreatedAt(conversation.getCreatedAt());
        // 私聊会话对方用户ID
        vo.setPeerId(targetUserId);
        // 私聊会话对方在线状态
        vo.setPeerStatus(targetUser.getStatus());
        log.info("[会话] 创建/获取私聊: convId={}, currentUserId={}, targetUserId={}",
                conversation.getId(), currentUserId, targetUserId);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteConversation(Long conversationId) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (!isMember(conversationId, userId)) {
            throw new BusinessException(ResultCode.NOT_CONVERSATION_MEMBER);
        }
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BusinessException(ResultCode.CONVERSATION_NOT_FOUND);
        }
        int type = conversation.getType() == null ? 0 : conversation.getType();
        // AI 会话暂不支持删除
        if (type == ConversationTypeEnum.TYPE_AI.getCode()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "AI会话暂不支持删除");
        }
        // 移除当前用户的会话成员记录(逻辑删除)
        conversationMemberMapper.delete(new LambdaQueryWrapper<ConversationMember>()
                .eq(ConversationMember::getConversationId, conversationId)
                .eq(ConversationMember::getUserId, userId));
        if (type == ConversationTypeEnum.TYPE_PRIVATE.getCode()) {
            // 私聊: 检查对方是否仍在会话中,若已无成员则归档会话
            Long remainCount = conversationMemberMapper.selectCount(
                    new LambdaQueryWrapper<ConversationMember>()
                            .eq(ConversationMember::getConversationId, conversationId));
            if (remainCount == null || remainCount == 0) {
                conversationMapper.deleteById(conversationId);
            }
        } else if (type == ConversationTypeEnum.TYPE_GROUP.getCode()) {
            // 群聊: 仅移除当前用户的 conversation_member 记录（从会话列表隐藏）
            // 保留 group_member 记录，用户仍为群组成员，可通过群组管理重新打开会话
            // 不减少 member_count（用户未真正退出群组）
        }
        log.info("[会话] 删除/退出会话: convId={}, userId={}, type={}", conversationId, userId, type);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void hideMessage(Long messageId) {
        Long userId = SecurityUtils.getCurrentUserId();
        Message message = messageMapper.selectById(messageId);
        if (message == null) {
            throw new BusinessException(ResultCode.MESSAGE_NOT_FOUND);
        }
        // 校验当前用户是该消息所在会话的成员
        if (!isMember(message.getConversationId(), userId)) {
            throw new BusinessException(ResultCode.NOT_CONVERSATION_MEMBER);
        }
        // 插入隐藏记录（唯一键约束避免重复）
        MessageHidden hidden = new MessageHidden();
        hidden.setUserId(userId);
        hidden.setMessageId(messageId);
        try {
            messageHiddenMapper.insert(hidden);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 已隐藏过，忽略
        }
        log.info("[消息] 用户隐藏消息: msgId={}, userId={}", messageId, userId);
    }

    @Override
    public List<ConversationVO> getMyGroupConversations() {
        Long userId = SecurityUtils.getCurrentUserId();
        return conversationMapper.selectGroupConversationsByGroupMember(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConversationVO rejoinGroupConversation(Long conversationId) {
        Long userId = SecurityUtils.getCurrentUserId();
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BusinessException(ResultCode.CONVERSATION_NOT_FOUND);
        }
        if (conversation.getType() != ConversationTypeEnum.TYPE_GROUP.getCode()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "仅群聊会话支持重新加入");
        }
        // 校验用户仍是群组成员（group_member 未删除）
        Long gmCount = groupMemberMapper.selectCount(new LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getConversationId, conversationId)
                .eq(GroupMember::getUserId, userId));
        if (gmCount == null || gmCount == 0) {
            throw new BusinessException(ResultCode.NOT_CONVERSATION_MEMBER, "您已不在该群组中");
        }
        // 恢复 conversation_member 记录（若已存在则重置加入时间，使重新加入者无法看到加入前的历史消息）
        conversationMemberMapper.restoreMember(conversationId, userId);
        // 返回会话 VO
        List<ConversationVO> list = conversationMapper.selectConversationsByUserId(userId);
        for (ConversationVO vo : list) {
            if (vo.getId().equals(conversationId)) {
                return vo;
            }
        }
        // 兜底：手动构建 VO
        ConversationVO vo = new ConversationVO();
        vo.setId(conversation.getId());
        vo.setName(conversation.getName());
        vo.setType(conversation.getType());
        vo.setAvatar(conversation.getAvatar());
        vo.setOwnerId(conversation.getOwnerId());
        vo.setLastMessage(conversation.getLastMessage());
        vo.setLastMessageAt(conversation.getLastMessageAt());
        vo.setLastMessageType(conversation.getLastMessageType());
        vo.setMemberCount(conversation.getMemberCount());
        vo.setDissolved(conversation.getDissolved());
        vo.setUnreadCount(0);
        vo.setCreatedAt(conversation.getCreatedAt());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearHistory(Long conversationId) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (!isMember(conversationId, userId)) {
            throw new BusinessException(ResultCode.NOT_CONVERSATION_MEMBER);
        }
        // 重置当前用户的 conversation_member.created_at 为当前时间，
        // 使 selectMessagesPage 按 m.created_at >= cm.created_at 过滤后不再返回历史消息。
        // 其他成员的 created_at 不受影响，仍可正常查看完整聊天记录。
        conversationMemberMapper.clearHistory(conversationId, userId);
        log.info("[会话] 清空聊天记录: convId={}, userId={}", conversationId, userId);
    }

    /**
     * 构建会话列表最后一条消息的预览文本。
     * - 图片: [图片]
     * - 文件: [文件]文件名（从 extra JSON 解析文件名）
     * - 其他: 原始内容（截断 200 字符）
     */
    private String buildLastMessagePreview(int messageType, String content, String extra) {
        if (messageType == MessageTypeEnum.TYPE_IMAGE.getCode()) {
            return "[图片]";
        }
        if (messageType == MessageTypeEnum.TYPE_FILE.getCode()) {
            String fileName = extractFileName(content, extra);
            return "[文件]" + fileName;
        }
        return StrUtil.maxLength(content, 200);
    }

    /**
     * 从 extra JSON 或 content URL 中提取文件名
     */
    private String extractFileName(String content, String extra) {
        if (StrUtil.isNotBlank(extra)) {
            try {
                com.fasterxml.jackson.databind.JsonNode node =
                        new com.fasterxml.jackson.databind.ObjectMapper().readTree(extra);
                if (node.has("fileName") && !node.get("fileName").isNull()) {
                    return node.get("fileName").asText();
                }
            } catch (Exception ignored) {
            }
        }
        // 从 URL 中提取
        if (StrUtil.isNotBlank(content)) {
            String[] parts = content.split("/");
            String last = parts[parts.length - 1];
            return StrUtil.isBlank(last) ? "未知文件" : last;
        }
        return "未知文件";
    }

    /**
     * 添加会话成员
     */
    private void addMember(Long conversationId, Long userId, Integer role) {
        ConversationMember member = new ConversationMember();
        member.setConversationId(conversationId);
        member.setUserId(userId);
        member.setRole(role);
        member.setUnreadCount(0);
        member.setMuted(ConversationMemberMutedEnum.MUTE_NO.getCode());
        member.setLastReadAt(LocalDateTime.now());
        conversationMemberMapper.insert(member);
    }
}
