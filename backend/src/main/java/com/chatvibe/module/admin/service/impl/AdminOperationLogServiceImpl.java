package com.chatvibe.module.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chatvibe.common.result.PageResult;
import com.chatvibe.module.admin.entity.OperationLog;
import com.chatvibe.module.admin.mapper.OperationLogMapper;
import com.chatvibe.module.admin.service.AdminOperationLogService;
import com.chatvibe.module.admin.vo.OperationLogVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 管理员操作日志查询服务实现
 *
 * @author Alu
 * @date 2026-08-06
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminOperationLogServiceImpl implements AdminOperationLogService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_EXPORT_SIZE = 10000;

    private final OperationLogMapper operationLogMapper;

    @Override
    public PageResult<OperationLogVO> getOperationLogs(String operator, String type, String startDate, String endDate, int page, int size) {
        int pageSize = Math.min(size, MAX_PAGE_SIZE);
        Page<OperationLog> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<OperationLog> wrapper = buildQueryWrapper(operator, type, startDate, endDate);
        wrapper.orderByDesc(OperationLog::getCreatedAt);
        Page<OperationLog> result = operationLogMapper.selectPage(pageParam, wrapper);

        List<OperationLogVO> records = new ArrayList<>();
        for (OperationLog operationLog : result.getRecords()) {
            records.add(toVO(operationLog));
        }
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), records);
    }

    @Override
    public List<OperationLogVO> getAllOperationLogs(String operator, String type, String startDate, String endDate) {
        LambdaQueryWrapper<OperationLog> wrapper = buildQueryWrapper(operator, type, startDate, endDate);
        wrapper.orderByDesc(OperationLog::getCreatedAt);
        wrapper.last("LIMIT " + MAX_EXPORT_SIZE);
        List<OperationLog> logs = operationLogMapper.selectList(wrapper);

        List<OperationLogVO> records = new ArrayList<>();
        for (OperationLog operationLog : logs) {
            records.add(toVO(operationLog));
        }
        return records;
    }

    /**
     * 构建查询条件 wrapper（复用于分页查询和导出）
     */
    private LambdaQueryWrapper<OperationLog> buildQueryWrapper(String operator, String type, String startDate, String endDate) {
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();

        // 操作者邮箱模糊匹配
        if (operator != null && !operator.isBlank()) {
            wrapper.like(OperationLog::getOperatorEmail, operator);
        }

        // 操作类型精确匹配
        if (type != null && !type.isBlank()) {
            wrapper.eq(OperationLog::getType, type);
        }

        // 日期范围过滤
        if (startDate != null && !startDate.isBlank()) {
            LocalDateTime startDateTime = LocalDate.parse(startDate).atStartOfDay();
            wrapper.ge(OperationLog::getCreatedAt, startDateTime);
        }
        if (endDate != null && !endDate.isBlank()) {
            LocalDateTime endDateTime = LocalDate.parse(endDate).atTime(23, 59, 59);
            wrapper.le(OperationLog::getCreatedAt, endDateTime);
        }

        return wrapper;
    }

    /**
     * OperationLog 转 OperationLogVO
     *
     * @param operationLog 操作日志实体
     * @return 操作日志VO
     */
    private OperationLogVO toVO(OperationLog operationLog) {
        OperationLogVO vo = new OperationLogVO();
        vo.setId(operationLog.getId());
        vo.setOperatorId(operationLog.getOperatorId());
        vo.setOperatorEmail(operationLog.getOperatorEmail());
        vo.setType(operationLog.getType());
        vo.setDetail(operationLog.getDetail());
        vo.setIp(operationLog.getIp());
        LocalDateTime createdAt = operationLog.getCreatedAt();
        if (createdAt != null) {
            vo.setCreatedAt(createdAt.format(DATE_TIME_FORMATTER));
        }
        return vo;
    }
}
