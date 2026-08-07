package com.chatvibe.module.admin.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 通知发送记录视图对象
 *
 * @author Alu
 * @date 2026-08-07
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationRecordVO {

    /**
     * 通知ID
     */
    private Long id;

    /**
     * 接收用户ID
     */
    private Long userId;

    /**
     * 接收用户昵称
     */
    private String userNickname;

    /**
     * 接收用户邮箱
     */
    private String userEmail;

    /**
     * 通知类型: 1-系统消息 2-好友请求 3-好友接受 4-好友删除 5-群邀请 6-被移除群 7-群解散 8-群转让
     */
    private Integer type;

    /**
     * 通知类型描述
     */
    private String typeDesc;

    /**
     * 通知标题
     */
    private String title;

    /**
     * 通知内容
     */
    private String content;

    /**
     * 附加数据JSON
     */
    private String extra;

    /**
     * 是否已读: 0-未读 1-已读
     */
    private Integer isRead;

    /**
     * 创建时间
     */
    private String createdAt;
}
