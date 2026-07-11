package com.chatvibe.module.friend.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.chatvibe.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 好友请求实体
 *
 * @author Alu
 * @date 2026-06-28
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("friend_request")
public class FriendRequest extends BaseEntity {

    /**
     * 状态: 0-待处理 1-已接受 2-已拒绝
     */
//    public static final int STATUS_PENDING = 0;
//    public static final int STATUS_ACCEPTED = 1;
//    public static final int STATUS_REJECTED = 2;

    /**
     * 请求发起者ID
     */
    private Long fromUid;

    /**
     * 接收者ID
     */
    private Long toUid;

    /**
     * 验证消息
     */
    private String message;

    /**
     * 状态: 0-待处理 1-已接受 2-已拒绝
     */
    private Integer status;
}
