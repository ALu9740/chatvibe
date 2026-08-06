package com.chatvibe.module.admin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.chatvibe.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 操作日志实体 (管理员操作审计)
 *
 * @author Alu
 * @date 2026-08-06
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("operation_log")
public class OperationLog extends BaseEntity {

    /**
     * 操作者ID
     */
    private Long operatorId;

    /**
     * 操作者邮箱
     */
    private String operatorEmail;

    /**
     * 操作类型: LOGIN/USER_BAN/USER_UNBAN/ROLE_CHANGE/PASSWORD_RESET/MESSAGE_DELETE/
     * GROUP_DISSOLVE/GROUP_TRANSFER/ANNOUNCEMENT_PUBLISH/ANNOUNCEMENT_WITHDRAW/
     * RATE_LIMIT_CONFIG/CIRCUIT_BREAKER_CONFIG/CACHE_CLEAR/ADMIN_ACCOUNT_MANAGE
     */
    private String type;

    /**
     * 操作详情
     */
    private String detail;

    /**
     * 操作IP
     */
    private String ip;
}
