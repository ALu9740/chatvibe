package com.chatvibe.module.admin.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 公告视图对象
 *
 * @author Alu
 * @date 2026-08-07
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnnouncementVO {

    /**
     * 公告ID
     */
    private Long id;

    /**
     * 公告标题
     */
    private String title;

    /**
     * 公告内容
     */
    private String content;

    /**
     * 范围: all-全部用户 specified-指定用户
     */
    private String scope;

    /**
     * 目标用户数
     */
    private Integer targetCount;

    /**
     * 状态: published-已发布 withdrawn-已撤回
     */
    private String status;

    /**
     * 创建时间
     */
    private String createdAt;

    /**
     * 创建者邮箱
     */
    private String createdBy;
}
