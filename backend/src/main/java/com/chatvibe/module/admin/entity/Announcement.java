package com.chatvibe.module.admin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.chatvibe.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 公告实体 (管理员发布系统公告)
 *
 * @author Alu
 * @date 2026-08-07
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("announcement")
public class Announcement extends BaseEntity {

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
     * 创建者邮箱
     */
    private String createdBy;
}
