package com.chatvibe.module.chat.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chatvibe.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 消息实体
 *
 * @author Alu
 * @date 2026-06-28
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("message")
public class Message extends BaseEntity {

    /**
     * 消息类型: 0-文本 1-图片 2-语音 3-文件 4-系统
     */
/*    public static final int TYPE_TEXT = 0;
    public static final int TYPE_IMAGE = 1;
    public static final int TYPE_VOICE = 2;
    public static final int TYPE_FILE = 3;
    public static final int TYPE_SYSTEM = 4;*/

    /**
     * 会话ID
     */
    private Long conversationId;

    /**
     * 发送者ID(0表示AI/系统)
     */
    private Long senderId;

    /**
     * 消息类型: 0-文本 1-图片 2-语音 3-文件 4-系统
     */
    private Integer type;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 附加信息(JSON)
     */
    private String extra;

    /**
     * 状态: 0-已发送 1-已送达 2-已读
     */
    private Integer status;

    /**
     * 发送者昵称（非数据库字段，查询时 JOIN user 表填充，用于群聊展示）
     */
    @TableField(exist = false)
    private String senderName;

    /**
     * 发送者头像（非数据库字段，查询时 JOIN user 表填充，用于群聊展示）
     */
    @TableField(exist = false)
    private String senderAvatar;
}
