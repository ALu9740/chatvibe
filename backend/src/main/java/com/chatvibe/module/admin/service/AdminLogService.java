package com.chatvibe.module.admin.service;

import com.chatvibe.module.admin.entity.OperationLog;
import com.chatvibe.module.admin.enums.OperationTypeEnum;
import com.chatvibe.module.admin.mapper.OperationLogMapper;
import com.chatvibe.security.LoginUser;
import com.chatvibe.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 管理员操作日志服务
 * 异步记录管理员操作日志，不影响主业务流程
 *
 * @author Alu
 * @date 2026-08-09
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminLogService {

    private final OperationLogMapper operationLogMapper;

    /**
     * 记录操作日志（异步）
     * 在调用方线程中先获取用户信息和IP，再异步写入数据库
     *
     * @param type   操作类型
     * @param detail 操作详情
     */
    public void log(OperationTypeEnum type, String detail) {
        LoginUser currentUser;
        try {
            currentUser = SecurityUtils.getCurrentUser();
        } catch (Exception e) {
            log.warn("记录操作日志时获取用户信息失败: {}", e.getMessage());
            return;
        }
        String ip = getClientIp();
        doLogAsync(currentUser.getId(), currentUser.getEmail(), type, detail, ip);
    }

    /**
     * 异步写入操作日志
     */
    @Async
    public void doLogAsync(Long operatorId, String operatorEmail, OperationTypeEnum type, String detail, String ip) {
        try {
            OperationLog operationLog = new OperationLog();
            operationLog.setOperatorId(operatorId);
            operationLog.setOperatorEmail(operatorEmail);
            operationLog.setType(type.getCode());
            operationLog.setDetail(detail);
            operationLog.setIp(ip);
            operationLogMapper.insert(operationLog);
        } catch (Exception e) {
            log.warn("异步记录操作日志失败: {}", e.getMessage());
        }
    }

    /**
     * 获取客户端IP
     */
    private String getClientIp() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String ip = request.getHeader("X-Forwarded-For");
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getHeader("X-Real-IP");
                }
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getRemoteAddr();
                }
                if (ip != null && ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }
        } catch (Exception e) {
            log.warn("获取客户端IP失败: {}", e.getMessage());
        }
        return "unknown";
    }
}
