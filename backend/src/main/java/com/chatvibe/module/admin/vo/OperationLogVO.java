package com.chatvibe.module.admin.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 操作日志视图对象
 *
 * @author Alu
 * @date 2026-08-06
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OperationLogVO {

    /**
     * 日志ID
     */
    private Long id;

    /**
     * 操作者ID
     */
    private Long operatorId;

    /**
     * 操作者邮箱
     */
    private String operatorEmail;

    /**
     * 操作类型
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

    /**
     * 创建时间
     */
    private String createdAt;
}
