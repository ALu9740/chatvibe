package com.chatvibe.module.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chatvibe.module.chat.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息 Mapper
 *
 * @author Alu
 * @date 2026-06-27
 */
@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    /**
     * 分页查询会话历史消息(倒序)
     * 过滤规则：
     *   1. 仅返回当前用户加入会话后的消息（m.created_at >= cm.created_at）
     *   2. 排除当前用户已隐藏（删除）的消息（LEFT JOIN message_hidden）
     *
     * @param conversationId 会话ID
     * @param userId         当前用户ID
     * @param lastId         上一页最后一条消息ID(可选)
     * @param size           每页大小
     * @return 消息列表
     */
    List<Message> selectMessagesPage(@Param("conversationId") Long conversationId,
                                     @Param("userId") Long userId,
                                     @Param("lastId") Long lastId,
                                     @Param("size") int size);

    /**
     * 统计某会话某时间之后的未读消息数
     *
     * @param conversationId 会话ID
     * @param userId         用户ID(排除自己发的)
     * @param lastReadAt     最后已读时间
     * @return 未读数
     */
    int countUnreadAfter(@Param("conversationId") Long conversationId,
                         @Param("userId") Long userId,
                         @Param("lastReadAt") LocalDateTime lastReadAt);
}
