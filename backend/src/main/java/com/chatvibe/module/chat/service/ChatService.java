package com.chatvibe.module.chat.service;

import com.chatvibe.module.chat.dto.SendMessageDTO;
import com.chatvibe.module.chat.entity.Conversation;
import com.chatvibe.module.chat.entity.Message;
import com.chatvibe.module.chat.vo.ConversationVO;

import java.util.List;

/**
 * 聊天服务接口
 *
 * @author Alu
 * @date 2026-06-27
 */
public interface ChatService {

    /**
     * 获取当前用户的会话列表
     *
     * @return 会话列表
     */
    List<ConversationVO> getConversationList();

    /**
     * 获取会话历史消息(分页)
     *
     * @param conversationId 会话ID
     * @param lastId         上一页最后一条消息ID(游标分页)
     * @param size           每页大小
     */
    List<Message> getHistoryMessages(Long conversationId, Long lastId, int size);

    /**
     * 发送消息(REST 备用)
     * 落库 + WebSocket 推送 + 更新未读
     */
    Message sendMessage(SendMessageDTO dto);

    /**
     * 发送消息(指定发送者, 供 WebSocket 上下文使用)
     * 落库 + WebSocket 推送 + 更新未读
     *
     * @param dto      消息内容
     * @param senderId 发送者ID
     */
    Message sendMessage(SendMessageDTO dto, Long senderId);

    /**
     * 标记会话已读
     *
     * @param conversationId 会话ID
     */
    void markAsRead(Long conversationId);

    /**
     * 切换会话消息免打扰状态（当前用户维度）
     *
     * @param conversationId 会话ID
     * @return 切换后的免打扰状态：true-已免打扰 false-已取消
     */
    boolean toggleMute(Long conversationId);

    /**
     * 切换会话置顶状态（当前用户维度）
     *
     * @param conversationId 会话ID
     * @return 切换后的置顶状态：true-已置顶 false-已取消
     */
    boolean togglePin(Long conversationId);

    /**
     * 创建私聊会话(两用户之间)
     *
     * @param userA 用户A
     * @param userB 用户B
     * @return 会话
     */
    Conversation createPrivateConversation(Long userA, Long userB);

    /**
     * 创建 AI 会话
     *
     * @param userId 用户ID
     * @return 会话
     */
    Conversation createAiConversation(Long userId);

    /**
     * 创建群聊会话
     *
     * @param ownerId  群主ID
     * @param name     群名称
     * @param memberIds 成员ID列表
     * @return 会话
     */
    Conversation createGroupConversation(Long ownerId, String name, List<Long> memberIds);

    /**
     * 校验用户是否为会话成员
     */
    boolean isMember(Long conversationId, Long userId);

    /**
     * 创建或获取私聊会话(当前用户与目标用户)
     *
     * @param targetUserId 目标用户ID
     * @return 会话视图
     */
    ConversationVO createOrGetPrivateConversation(Long targetUserId);

    /**
     * 删除/退出会话(从当前用户会话列表移除)
     * - 私聊: 移除当前用户成员记录,双方都移除则归档会话
     * - AI: 移除成员记录并归档会话
     * - 群聊: 仅移除当前用户的 conversation_member 记录(保留 group_member,可通过群组管理重新打开)
     *
     * @param conversationId 会话ID
     */
    void deleteConversation(Long conversationId);

    /**
     * 隐藏（删除）单条消息（仅对当前用户隐藏，其他用户仍可见）
     *
     * @param messageId 消息ID
     */
    void hideMessage(Long messageId);

    /**
     * 获取当前用户作为群组成员（group_member）的群聊会话列表。
     * 包含已从会话列表删除但未退出群组的会话，用于群组管理弹窗。
     *
     * @return 群聊会话列表
     */
    List<ConversationVO> getMyGroupConversations();

    /**
     * 重新加入群聊会话（恢复 conversation_member 记录）。
     * 用于用户删除群聊会话后通过群组管理重新打开。
     *
     * @param conversationId 会话ID
     * @return 会话视图
     */
    ConversationVO rejoinGroupConversation(Long conversationId);

    /**
     * 清空当前用户在指定会话中的聊天记录（仅对当前用户隐藏，其他成员仍可见）。
     *
     * @param conversationId 会话ID
     */
    void clearHistory(Long conversationId);
}
