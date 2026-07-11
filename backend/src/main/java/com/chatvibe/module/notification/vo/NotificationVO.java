package com.chatvibe.module.notification.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知视图对象
 *
 * @author Alu
 * @date 2026-07-02
 */
@Data
public class NotificationVO {

    private Long id;

    /**
     * 通知类型: 1-系统消息 2-好友请求 3-好友接受 4-好友删除 5-群邀请 6-被移除群 7-群解散
     */
    private Integer type;

    /**
     * 通知类型描述
     */
    private String typeDesc;

    private String title;

    private String content;

    /**
     * 附加数据JSON
     */
    private String extra;

    /**
     * 是否已读: 0-未读 1-已读
     */
    private Integer isRead;

    private LocalDateTime createdAt;
}
