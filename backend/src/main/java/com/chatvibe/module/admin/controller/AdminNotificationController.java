package com.chatvibe.module.admin.controller;

import com.chatvibe.common.result.PageResult;
import com.chatvibe.common.result.Result;
import com.chatvibe.module.admin.service.AdminNotificationService;
import com.chatvibe.module.admin.vo.NotificationRecordVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员通知发送记录接口
 * 只读查询系统自动通知的发送记录
 *
 * @author Alu
 * @date 2026-08-07
 */
@RestController
@RequestMapping("/api/admin/notifications")
@RequiredArgsConstructor
public class AdminNotificationController {

    private final AdminNotificationService adminNotificationService;

    /**
     * 分页查询通知发送记录
     *
     * @param type      通知类型: 1-系统消息 2-好友请求 3-好友接受 4-好友删除 5-群邀请 6-被移除群 7-群解散 8-群转让
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @param keyword   搜索关键词，匹配用户昵称/邮箱
     * @param isRead    已读状态: null-全部 0-未读 1-已读
     * @param page      页码
     * @param size      每页大小
     * @return 通知记录分页列表
     */
    @GetMapping
    public Result<PageResult<NotificationRecordVO>> getNotificationList(
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer isRead,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(adminNotificationService.getNotificationList(type, startDate, endDate, keyword, isRead, page, size));
    }
}
