package com.chatvibe.module.admin.service;

import com.chatvibe.common.result.PageResult;
import com.chatvibe.module.admin.vo.OperationLogVO;

import java.util.List;

/**
 * 管理员操作日志查询服务接口
 *
 * @author Alu
 * @date 2026-08-06
 */
public interface AdminOperationLogService {

    /**
     * 分页查询操作日志
     *
     * @param operator  操作者邮箱(模糊匹配)
     * @param type      操作类型
     * @param startDate 开始日期(yyyy-MM-dd)
     * @param endDate   结束日期(yyyy-MM-dd)
     * @param page      页码
     * @param size      每页大小
     * @return 操作日志分页结果
     */
    PageResult<OperationLogVO> getOperationLogs(String operator, String type, String startDate, String endDate, int page, int size);

    /**
     * 查询全部操作日志（用于导出Excel，最多10000条）
     *
     * @param operator  操作者邮箱(模糊匹配)
     * @param type      操作类型
     * @param startDate 开始日期(yyyy-MM-dd)
     * @param endDate   结束日期(yyyy-MM-dd)
     * @return 操作日志列表
     */
    List<OperationLogVO> getAllOperationLogs(String operator, String type, String startDate, String endDate);
}
