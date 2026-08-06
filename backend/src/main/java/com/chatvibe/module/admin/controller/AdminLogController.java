package com.chatvibe.module.admin.controller;

import com.chatvibe.common.result.PageResult;
import com.chatvibe.common.result.Result;
import com.chatvibe.module.admin.service.AdminOperationLogService;
import com.chatvibe.module.admin.vo.OperationLogVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员操作日志接口
 *
 * @author Alu
 * @date 2026-08-06
 */
@RestController
@RequestMapping("/api/admin/logs")
@RequiredArgsConstructor
public class AdminLogController {

    private final AdminOperationLogService adminOperationLogService;

    /**
     * 分页查询操作日志
     *
     * @param operator  操作者邮箱
     * @param type      操作类型
     * @param startDate 开始日期 yyyy-MM-dd
     * @param endDate   结束日期 yyyy-MM-dd
     * @param page      页码
     * @param size      每页大小
     * @return 操作日志分页结果
     */
    @GetMapping
    public Result<PageResult<OperationLogVO>> getOperationLogs(
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(adminOperationLogService.getOperationLogs(operator, type, startDate, endDate, page, size));
    }
}
