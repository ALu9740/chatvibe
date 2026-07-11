package com.chatvibe.module.chat.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.chatvibe.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 会话实体
 *
 * @author Alu
 * @date 2026-06-27
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("conversation")
public class Conversation extends BaseEntity {

    /**
     * 会话类型: 1-私聊 2-群聊 3-AI
     */
/*    public static final int TYPE_PRIVATE = 1;
    public static final int TYPE_GROUP = 2;
    public static final int TYPE_AI = 3;*/

    /**
     * 会话名称(群聊/AI)
     */
    private String name;

    /**
     * 会话类型: 1-私聊 2-群聊 3-AI
     */
    private Integer type;

    /**
     * 会话头像
     */
    private String avatar;

    /**
     * 群主ID(群聊)
     */
    private Long ownerId;

    /**
     * 最后一条消息内容(图片/文件已转为预览文本)
     */
    private String lastMessage;

    /**
     * 最后消息时间
     */
    private LocalDateTime lastMessageAt;

    /**
     * 最后一条消息类型: 0-文本 1-图片 3-文件 4-系统
     */
    private Integer lastMessageType;

    /**
     * 成员数
     */
    private Integer memberCount;

    /**
     * 群组是否已解散: 0-否 1-是
     * 仅群聊有效；解散后成员保留会话但禁言，需手动删除会话
     */
    private Integer dissolved;
}
