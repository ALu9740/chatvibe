package com.chatvibe.module.user.enums;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

/**
 * 用户头像信息状态枚举
 *
 * @author Alu
 * @date 2026-06-30
 */
@Getter
@AllArgsConstructor
public enum UserAvatarEnum {

    AVATAR_DATA_EMPTY(10001, "头像数据不能为空"),
    AVATAR_BASE64_DECODE_FAILED(10002, "头像 Base64 解码失败"),
    AVATAR_SIZE_EXCEEDED(10003, "头像大小不能超过 2MB"),
    AVATAR_UPLOAD_FAILED(10004, "头像上传失败，请稍后重试");

    private final Integer code;
    private final String message;
}
