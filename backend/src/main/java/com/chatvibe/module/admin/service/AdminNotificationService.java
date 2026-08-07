package com.chatvibe.module.admin.service;

import com.chatvibe.common.result.PageResult;
import com.chatvibe.module.admin.vo.NotificationRecordVO;

/**
 * 管理员通知查询服务接口
 * 只读查询，不支持修改
 *
 * @author Alu
 * @date 2026-08-07
 */
public interface AdminNotificationService {

    /**
     * 分页查询通知发送记录
     *
     * @param type      通知类型
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @param keyword   搜索关键词，匹配用户昵称/邮箱
     * @param isRead    已读状态: null-全部 0-未读 1-已读
     * @param page      页码
     * @param size      每页大小
     * @return 通知记录分页结果
     */
    PageResult<NotificationRecordVO> getNotificationList(Integer type, String startDate, String endDate, String keyword, Integer isRead, int page, int size);
}
