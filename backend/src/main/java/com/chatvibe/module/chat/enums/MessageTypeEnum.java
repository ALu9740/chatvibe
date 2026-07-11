package com.chatvibe.module.chat.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 消息类型枚举
 *
 * @author Alu
 * @date 2026-06-30
 */
@Getter
@AllArgsConstructor
public enum MessageTypeEnum {
    /**
     * 消息类型: 0-文本 1-图片 2-语音 3-文件 4-系统
     */
    TYPE_TEXT(0, "文本"),
    TYPE_IMAGE(1, "图片"),
    TYPE_VOICE(2, "语音"),
    TYPE_FILE(3, "文件"),
    TYPE_SYSTEM(4, "系统");
    private final Integer code;
    private final String message;
}
