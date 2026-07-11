package com.chatvibe.module.chat.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.chatvibe.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 消息隐藏实体（用户级消息删除：仅对操作用户隐藏，其他用户仍可见）
 *
 * @author Alu
 * @date 2026-06-29
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("message_hidden")
public class MessageHidden extends BaseEntity {

    /** 隐藏消息的用户ID */
    private Long userId;

    /** 被隐藏的消息ID */
    private Long messageId;
}
