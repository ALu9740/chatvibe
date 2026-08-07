package com.chatvibe.module.admin.vo;

import lombok.Data;

/**
 * 消息审计视图对象（管理员视角）
 *
 * @author Alu
 * @date 2026-08-06
 */
@Data
public class AuditMessageVO {

    private Long id;

    private Long conversationId;

    private String conversationName;

    /**
     * 会话类型: 私聊/群聊/AI
     */
    private String conversationType;

    private Long senderId;

    private String senderName;

    /**
     * 消息类型: TEXT/IMAGE/FILE/SYSTEM/AI
     */
    private String type;

    private String content;

    /**
     * 附加信息(JSON, 图片/文件消息含 fileName/fileSize)
     */
    private String extra;

    /**
     * 发送时间(yyyy-MM-dd HH:mm:ss)
     */
    private String createdAt;

    /**
     * 是否已被管理员删除(逻辑删除)
     */
    private Boolean deleted;

    /**
     * 是否已被用户隐藏(用户级删除)
     */
    private Boolean hidden;
}
