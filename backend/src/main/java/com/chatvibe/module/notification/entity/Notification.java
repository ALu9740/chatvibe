package com.chatvibe.module.notification.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chatvibe.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 消息通知实体
 *
 * @author Alu
 * @date 2026-07-02
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("notification")
public class Notification extends BaseEntity {

    /**
     * 接收通知的用户ID
     */
    private Long userId;

    /**
     * 通知类型: 1-系统消息 2-好友请求 3-好友接受 4-好友删除 5-群邀请 6-被移除群 7-群解散
     */
    private Integer type;

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
    @TableField("is_read")
    private Integer isRead;
}
