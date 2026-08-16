package com.chatvibe.module.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chatvibe.module.chat.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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

    /**
     * 管理员审计：分页查询消息（含已删除记录，不经过 MyBatis-Plus 逻辑删除过滤）
     *
     * @param keyword        关键词(模糊匹配 content)
     * @param senderId       发送者ID
     * @param conversationId 会话ID
     * @param type           消息类型(0-文本 1-图片 2-语音 3-文件 4-系统)
     * @param aiOnly         是否仅查AI消息(senderId=0 且 type=0)
     * @param startTime      起始时间
     * @param endTime        结束时间
     * @param offset         偏移量
     * @param size           每页大小
     * @return 消息列表(含已删除)
     */
    List<Message> selectAuditMessagesPage(@Param("keyword") String keyword,
                                          @Param("senderId") Long senderId,
                                          @Param("conversationId") Long conversationId,
                                          @Param("type") Integer type,
                                          @Param("aiOnly") Boolean aiOnly,
                                          @Param("startTime") LocalDateTime startTime,
                                          @Param("endTime") LocalDateTime endTime,
                                          @Param("offset") int offset,
                                          @Param("size") int size);

    /**
     * 管理员审计：统计消息总数（含已删除记录）
     */
    long countAuditMessages(@Param("keyword") String keyword,
                            @Param("senderId") Long senderId,
                            @Param("conversationId") Long conversationId,
                            @Param("type") Integer type,
                            @Param("aiOnly") Boolean aiOnly,
                            @Param("startTime") LocalDateTime startTime,
                            @Param("endTime") LocalDateTime endTime);

    /**
     * 统计指定时间范围内每天的 AI 回复消息数（sender_id=0, type=0）
     * 用于仪表盘 AI 调用趋势图
     *
     * @param startTime 起始时间
     * @return 每日统计列表，每项包含 date(java.time.LocalDate) 和 count(Long)
     */
    List<Map<String, Object>> countAiMessagesDaily(@Param("startTime") LocalDateTime startTime);

    /**
     * 统计指定时间范围内 AI 回复消息的供应商分布
     * 优先使用消息级别的 provider 字段，旧消息回退到会话级别的 ai_provider/provider
     *
     * @param startTime 起始时间
     * @return 供应商分布列表，每项包含 provider(String) 和 count(Long)
     */
    List<Map<String, Object>> getAiProviderBreakdown(@Param("startTime") LocalDateTime startTime);
}
