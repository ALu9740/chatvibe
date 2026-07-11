package com.chatvibe.module.group.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chatvibe.module.group.entity.GroupMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 群组成员 Mapper
 *
 * @author Alu
 * @date 2026-07-01
 */
@Mapper
public interface GroupMemberMapper extends BaseMapper<GroupMember> {

    /**
     * 查询群成员记录（原生 SQL，绕过 @TableLogic 自动过滤 deleted=1）。
     * 用于群邀请 upsert 判断：若存在已逻辑删除的记录则恢复，否则才 insert，
     * 避免违反 UNIQUE KEY uk_group_user(conversation_id, user_id) 导致 500 错误。
     *
     * @param conversationId 群会话ID
     * @param userId         用户ID
     * @return 群成员记录（可能为 null，或 deleted=0/1 的记录）
     */
    @Select("SELECT * FROM group_member WHERE conversation_id = #{conversationId} AND user_id = #{userId}")
    GroupMember selectIgnoreDeleted(@Param("conversationId") Long conversationId,
                                    @Param("userId") Long userId);

    /**
     * 恢复（取消逻辑删除）群成员记录，并重置加入时间为当前时间。
     * 用于群邀请场景：用户曾被移除/退出群后再次被邀请入群时，恢复原记录而非新建。
     *
     * @param conversationId 群会话ID
     * @param userId         用户ID
     */
    @Update("UPDATE group_member SET deleted = 0, role = 0, join_time = NOW(), updated_at = NOW() " +
            "WHERE conversation_id = #{conversationId} AND user_id = #{userId}")
    void restoreGroupMember(@Param("conversationId") Long conversationId,
                             @Param("userId") Long userId);
}
