package com.chatvibe.module.admin.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 管理员操作类型枚举
 *
 * @author Alu
 * @date 2026-08-06
 */
@Getter
@AllArgsConstructor
public enum OperationTypeEnum {
    LOGIN("LOGIN", "管理员登录"),
    LOGOUT("LOGOUT", "管理员登出"),
    USER_BAN("USER_BAN", "封禁用户"),
    USER_UNBAN("USER_UNBAN", "解封用户"),
    ROLE_CHANGE("ROLE_CHANGE", "修改用户角色"),
    PASSWORD_RESET("PASSWORD_RESET", "重置用户密码"),
    MESSAGE_DELETE("MESSAGE_DELETE", "删除消息"),
    GROUP_DISSOLVE("GROUP_DISSOLVE", "解散群组"),
    GROUP_TRANSFER("GROUP_TRANSFER", "转让群主"),
    ANNOUNCEMENT_PUBLISH("ANNOUNCEMENT_PUBLISH", "发布公告"),
    ANNOUNCEMENT_WITHDRAW("ANNOUNCEMENT_WITHDRAW", "撤回公告"),
    RATE_LIMIT_CONFIG("RATE_LIMIT_CONFIG", "修改限流配置"),
    CIRCUIT_BREAKER_CONFIG("CIRCUIT_BREAKER_CONFIG", "修改熔断配置"),
    CACHE_CLEAR("CACHE_CLEAR", "清除缓存"),
    ADMIN_ACCOUNT_MANAGE("ADMIN_ACCOUNT_MANAGE", "管理员账号管理"),
    AI_PROVIDER_ADD("AI_PROVIDER_ADD", "添加AI供应商"),
    AI_PROVIDER_UPDATE("AI_PROVIDER_UPDATE", "更新AI供应商"),
    AI_PROVIDER_DELETE("AI_PROVIDER_DELETE", "删除AI供应商"),
    FAILOVER_CONFIG("FAILOVER_CONFIG", "故障转移配置"),
    EMAIL_CONFIG("EMAIL_CONFIG", "邮件配置");

    private final String code;
    private final String message;
}
