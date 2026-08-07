package com.chatvibe.module.admin.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 公告发布事件（通过 RabbitMQ 异步处理通知创建）
 *
 * @author Alu
 * @date 2026-08-07
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementPublishEvent implements Serializable {

    /**
     * 公告ID
     */
    private Long announcementId;

    /**
     * 公告标题
     */
    private String title;

    /**
     * 公告内容
     */
    private String content;

    /**
     * 目标用户ID列表
     */
    private List<Long> targetUserIds;
}
