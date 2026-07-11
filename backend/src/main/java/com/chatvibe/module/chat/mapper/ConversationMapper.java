package com.chatvibe.module.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chatvibe.module.chat.entity.Conversation;
import com.chatvibe.module.chat.vo.ConversationVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 会话 Mapper
 *
 * @author Alu
 * @date 2026-06-27
 */
@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {

    /**
     * 查询用户参与的会话列表(带未读数和最后消息)
     *
     * @param userId 用户ID
     * @return 会话列表
     */
    List<ConversationVO> selectConversationsByUserId(@Param("userId") Long userId);

    /**
     * 查询两用户之间的私聊会话
     *
     * @param userA 用户A
     * @param userB 用户B
     * @return 私聊会话
     */
    Conversation selectPrivateConversation(@Param("userA") Long userA, @Param("userB") Long userB);

    /**
     * 查询两用户之间的私聊会话（忽略成员逻辑删除状态）。
     * 用于删除会话后重新创建：找到已存在的会话并恢复成员记录，而非新建会话。
     *
     * @param userA 用户A
     * @param userB 用户B
     * @return 私聊会话（含已逻辑删除成员的）
     */
    Conversation selectPrivateConversationAnyStatus(@Param("userA") Long userA, @Param("userB") Long userB);

    /**
     * 查询用户通过 group_member 仍为成员的群聊会话列表（忽略 conversation_member 是否已删除）。
     * 用于群组管理弹窗展示"我加入的群组"，支持重新打开已从会话列表删除的群聊。
     *
     * @param userId 用户ID
     * @return 群聊会话列表
     */
    List<ConversationVO> selectGroupConversationsByGroupMember(@Param("userId") Long userId);
}
