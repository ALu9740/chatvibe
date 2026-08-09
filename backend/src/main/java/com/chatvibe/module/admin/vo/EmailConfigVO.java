package com.chatvibe.module.admin.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 邮件配置视图对象
 *
 * @author Alu
 * @date 2026-08-09
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EmailConfigVO {

    /**
     * SMTP 主机
     */
    private String host;

    /**
     * SMTP 端口
     */
    private Integer port;

    /**
     * 用户名(SMTP 登录账号)
     */
    private String username;

    /**
     * SMTP 授权码/密码
     */
    private String password;

    /**
     * 发件邮箱
     */
    private String fromEmail;

    /**
     * 是否启用 SSL(端口465用SSL, 端口587用STARTTLS)
     */
    private Boolean sslEnabled;
}
