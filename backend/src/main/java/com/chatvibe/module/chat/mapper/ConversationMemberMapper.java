package com.chatvibe.module.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chatvibe.module.chat.entity.ConversationMember;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 会话成员 Mapper
 *
 * @author Alu
 * @date 2026-06-27
 */
@Mapper
public interface ConversationMemberMapper extends BaseMapper<ConversationMember> {

    /**
     * 恢复（取消逻辑删除）会话成员记录，并重置加入时间为当前时间。
     * 用于用户删除会话后重新加入/重新创建会话的场景，确保用户无法看到加入前的历史消息。
     *
     * @param conversationId 会话ID
     * @param userId         用户ID
     */
    void restoreMember(@Param("conversationId") Long conversationId, @Param("userId") Long userId);

    /**
     * 清空当前用户在指定会话中的聊天记录（仅对当前用户隐藏历史消息，其他成员不受影响）。
     * 通过重置 conversation_member.created_at 为当前时间，使 selectMessagesPage
     * 按 m.created_at >= cm.created_at 过滤后不再返回加入前的历史消息。
     *
     * @param conversationId 会话ID
     * @param userId         用户ID
     * @return 受影响行数（0 表示未找到记录，1 表示成功）
     */
    int clearHistory(@Param("conversationId") Long conversationId, @Param("userId") Long userId);

    /**
     * 私聊场景：恢复已删除会话的成员记录（新消息到达时，会话重新出现在对方列表中）。
     * 仅取消逻辑删除并重置 created_at 为当前时间，使恢复者只能看到新消息及之后的消息。
     *
     * @param conversationId 会话ID
     * @param senderId       发送者ID（不恢复发送者自己）
     */
    void restoreDeletedMembers(@Param("conversationId") Long conversationId, @Param("senderId") Long senderId);

    /**
     * 群聊场景：恢复已删除会话的成员记录，仅限仍在群中的成员（group_member 未删除）。
     * 已退出群的成员不会被恢复。
     *
     * @param conversationId 会话ID
     * @param senderId       发送者ID（不恢复发送者自己）
     */
    void restoreDeletedGroupMembers(@Param("conversationId") Long conversationId, @Param("senderId") Long senderId);

    /**
     * 查询用户的好友ID列表（原生SQL，绕过 @TableLogic 自动过滤）。
     * 删除会话仅从会话列表隐藏，不应影响好友关系，因此需包含已逻辑删除的 conversation_member 记录。
     *
     * @param userId 当前用户ID
     * @return 好友用户ID列表
     */
    List<Long> selectFriendIdsIgnoreDeleted(@Param("userId") Long userId);

    /**
     * 物理删除指定会话中两个用户的成员记录（彻底解除好友关系）。
     * 用于删除好友场景：区别于删除会话的逻辑删除（隐藏会话，清除聊天记录，保留好友关系），
     * 物理删除使记录彻底不存在，selectFriendIdsIgnoreDeleted 将查不到该好友。
     *
     * @param conversationId 私聊会话ID
     * @param userIdA        用户A ID
     * @param userIdB        用户B ID
     */
    void physicalDeleteMembers(@Param("conversationId") Long conversationId,
                               @Param("userIdA") Long userIdA,
                               @Param("userIdB") Long userIdB);

    /**
     * 查询成员记录（原生 SQL，绕过 @TableLogic 自动过滤 deleted=1）。
     * 用于群邀请 upsert 判断：若存在已逻辑删除的记录则恢复，否则才 insert，
     * 避免违反 UNIQUE KEY uk_conv_user(conversation_id, user_id) 导致 500 错误。
     *
     * @param conversationId 会话ID
     * @param userId         用户ID
     * @return 成员记录（可能为 null，或 deleted=0/1 的记录）
     */
    ConversationMember selectIgnoreDeleted(@Param("conversationId") Long conversationId,
                                            @Param("userId") Long userId);
}
