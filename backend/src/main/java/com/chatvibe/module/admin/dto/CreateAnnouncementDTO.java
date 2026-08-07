package com.chatvibe.module.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 创建公告 DTO
 *
 * @author Alu
 * @date 2026-08-07
 */
@Data
public class CreateAnnouncementDTO {

    /**
     * 公告标题（必填，≤100字符）
     */
    @NotBlank(message = "公告标题不能为空")
    @Size(max = 100, message = "公告标题不能超过100个字符")
    private String title;

    /**
     * 公告内容（必填，≤5000字符，纯文本）
     */
    @NotBlank(message = "公告内容不能为空")
    @Size(max = 5000, message = "公告内容不能超过5000个字符")
    private String content;

    /**
     * 范围: all-全部用户 specified-指定用户
     */
    @NotBlank(message = "公告范围不能为空")
    private String scope;

    /**
     * 指定用户ID列表(scope=specified 时使用，最多1000人)
     */
    @Size(max = 1000, message = "指定用户最多选择1000人")
    private List<Long> targetUserIds;
}
