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
    @Update("UPDATE conversation_member SET deleted = 0, unread_count = 0, " +
            "last_read_at = NOW(), created_at = NOW(), updated_at = NOW() " +
            "WHERE conversation_id = #{conversationId} AND user_id = #{userId}")
    void restoreMember(@Param("conversationId") Long conversationId, @Param("userId") Long userId);

    /**
     * 清空当前用户在指定会话中的聊天记录（仅对当前用户隐藏历史消息，其他成员不受影响）。
     * 通过重置 conversation_member.created_at 为当前时间，使 selectMessagesPage
     * 按 m.created_at >= cm.created_at 过滤后不再返回加入前的历史消息。
     *
     * @param conversationId 会话ID
     * @param userId         用户ID
     */
    @Update("UPDATE conversation_member SET created_at = NOW(), unread_count = 0, " +
            "last_read_at = NOW(), updated_at = NOW() " +
            "WHERE conversation_id = #{conversationId} AND user_id = #{userId}")
    void clearHistory(@Param("conversationId") Long conversationId, @Param("userId") Long userId);

    /**
     * 私聊场景：恢复已删除会话的成员记录（新消息到达时，会话重新出现在对方列表中）。
     * 仅取消逻辑删除并重置 created_at 为当前时间，使恢复者只能看到新消息及之后的消息。
     *
     * @param conversationId 会话ID
     * @param senderId       发送者ID（不恢复发送者自己）
     */
    @Update("UPDATE conversation_member SET deleted = 0, created_at = NOW(), updated_at = NOW() " +
            "WHERE conversation_id = #{conversationId} " +
            "AND user_id != #{senderId} " +
            "AND deleted = 1")
    void restoreDeletedMembers(@Param("conversationId") Long conversationId, @Param("senderId") Long senderId);

    /**
     * 群聊场景：恢复已删除会话的成员记录，仅限仍在群中的成员（group_member 未删除）。
     * 已退出群的成员不会被恢复。
     *
     * @param conversationId 会话ID
     * @param senderId       发送者ID（不恢复发送者自己）
     */
    @Update("UPDATE conversation_member cm INNER JOIN group_member gm " +
            "ON gm.conversation_id = cm.conversation_id AND gm.user_id = cm.user_id AND gm.deleted = 0 " +
            "SET cm.deleted = 0, cm.created_at = NOW(), cm.updated_at = NOW() " +
            "WHERE cm.conversation_id = #{conversationId} " +
            "AND cm.user_id != #{senderId} " +
            "AND cm.deleted = 1")
    void restoreDeletedGroupMembers(@Param("conversationId") Long conversationId, @Param("senderId") Long senderId);

    /**
     * 查询用户的好友ID列表（原生SQL，绕过 @TableLogic 自动过滤）。
     * 删除会话仅从会话列表隐藏，不应影响好友关系，因此需包含已逻辑删除的 conversation_member 记录。
     *
     * @param userId 当前用户ID
     * @return 好友用户ID列表
     */
    @Select("SELECT DISTINCT cm2.user_id FROM conversation_member cm1 " +
            "INNER JOIN conversation c ON cm1.conversation_id = c.id " +
            "INNER JOIN conversation_member cm2 ON cm2.conversation_id = c.id AND cm2.user_id != cm1.user_id " +
            "WHERE cm1.user_id = #{userId} AND c.type = 1")
    List<Long> selectFriendIdsIgnoreDeleted(@Param("userId") Long userId);

    /**
     * 物理删除指定会话中两个用户的成员记录（彻底解除好友关系）。
     * 用于删除好友场景：区别于删除会话的逻辑删除（仅隐藏列表，保留好友关系），
     * 物理删除使记录彻底不存在，selectFriendIdsIgnoreDeleted 将查不到该好友。
     *
     * @param conversationId 私聊会话ID
     * @param userIdA        用户A ID
     * @param userIdB        用户B ID
     */
    @Delete("DELETE FROM conversation_member WHERE conversation_id = #{conversationId} " +
            "AND user_id IN (#{userIdA}, #{userIdB})")
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
    @Select("SELECT * FROM conversation_member WHERE conversation_id = #{conversationId} AND user_id = #{userId}")
    ConversationMember selectIgnoreDeleted(@Param("conversationId") Long conversationId,
                                            @Param("userId") Long userId);
}
