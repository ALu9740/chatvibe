package com.chatvibe.module.admin.service;

import com.chatvibe.common.result.PageResult;
import com.chatvibe.module.admin.vo.AuditMessageVO;

/**
 * 管理员消息审计服务
 *
 * @author Alu
 * @date 2026-08-07
 */
public interface AdminMessageService {

    /**
     * 搜索/审计消息
     *
     * @param keyword       关键字(消息内容)
     * @param senderId      发送者ID
     * @param conversationId 会话ID
     * @param type          消息类型: TEXT/IMAGE/FILE/SYSTEM/AI
     * @param startDate     起始日期(yyyy-MM-dd)
     * @param endDate       结束日期(yyyy-MM-dd)
     * @param page          页码
     * @param size          每页大小
     * @return 消息分页结果
     */
    PageResult<AuditMessageVO> searchMessages(String keyword, Long senderId, Long conversationId, String type, String startDate, String endDate, int page, int size);

    /**
     * 删除消息（逻辑删除）
     *
     * @param messageId 消息ID
     * @param reason    删除原因
     * @return 是否成功
     */
    boolean deleteMessage(Long messageId, String reason);
}
