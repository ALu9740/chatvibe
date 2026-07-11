package com.chatvibe.module.file.vo;

import lombok.Data;

/**
 * 文件上传返回 VO
 *
 * @author Alu
 * @date 2026-07-01
 */
@Data
public class FileUploadVO {

    /**
     * 可访问 URL
     */
    private String url;

    /**
     * 原始文件名
     */
    private String fileName;

    /**
     * 文件大小(字节)
     */
    private Long fileSize;

    /**
     * 文件 MIME 类型
     */
    private String fileType;
}
