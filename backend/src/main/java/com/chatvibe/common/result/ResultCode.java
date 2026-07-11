package com.chatvibe.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 系统响应状态码枚举
 *
 * @author Alu
 * @date 2026-06-27
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    /* 成功 */
    SUCCESS(200, "操作成功"),

    /* 通用错误 1xxx */
    FAIL(1000, "操作失败"),
    PARAM_INVALID(1001, "参数校验失败"),
    LOGIN_EXPIRED(1002, "登录失效，请重新登录"),
    FORBIDDEN(1003, "无权限访问"),
    NOT_FOUND(1004, "资源不存在"),
    METHOD_NOT_ALLOWED(1005, "请求方法不支持"),
    SYSTEM_ERROR(1006, "系统繁忙，请稍后再试"),
    TOO_MANY_REQUESTS(1007, "请求过于频繁，请稍后再试"),

    /* 认证模块 2xxx */
    EMAIL_OR_PASSWORD_ERROR(2001, "邮箱或密码错误"),
    ACCOUNT_EXISTS(2002, "账号已存在"),
    EMAIL_EXISTS(2003, "邮箱已注册"),
    CODE_SEND_TOO_FREQUENT(2004, "验证码发送过于频繁，请60秒后再试"),
    CODE_INVALID(2005, "验证码无效或已过期"),
    CODE_NOT_MATCH(2006, "验证码不正确"),
    TOKEN_INVALID(2007, "Token 无效"),
    TOKEN_EXPIRED(2008, "Token 已过期"),
    ACCOUNT_DISABLED(2009, "账号已被禁用"),
    OLD_PASSWORD_ERROR(2010, "旧密码不正确"),
    ACCOUNT_LOGIN_ELSEWHERE(2011, "当前账号已在其他设备登录，您已被强制下线"),
    EMAIL_NOT_FOUND(2012, "邮箱未注册"),

    /* 用户模块 3xxx */
    USER_NOT_FOUND(3001, "用户不存在"),
    USER_ALREADY_FRIEND(3002, "已经是好友关系"),
    FRIEND_REQUEST_EXISTS(3003, "已发送过好友请求"),
    FRIEND_REQUEST_NOT_FOUND(3004, "好友请求不存在"),
    FRIEND_REQUEST_RECEIVED(3005, "对方已向你发送好友申请，请先处理"),

    /* 聊天模块 4xxx */
    CONVERSATION_NOT_FOUND(4001, "会话不存在"),
    NOT_CONVERSATION_MEMBER(4002, "非会话成员"),
    MESSAGE_NOT_FOUND(4003, "消息不存在"),

    /* 群组模块 5xxx */
    GROUP_NOT_FOUND(5001, "群组不存在"),
    NOT_GROUP_OWNER(5002, "非群主，无权操作"),
    ALREADY_GROUP_MEMBER(5003, "已是群成员"),

    /* AI 模块 6xxx */
    AI_SERVICE_ERROR(6001, "AI 服务异常"),
    AI_LIMIT_EXCEEDED(6002, "AI 调用次数超限");

    private final Integer code;
    private final String message;
}
