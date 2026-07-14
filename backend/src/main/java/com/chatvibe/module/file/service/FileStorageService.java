package com.chatvibe.module.file.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储服务接口
 *
 * @author Alu
 * @date 2026-07-14
 */
public interface FileStorageService {
    /**
     * 上传文件
     *
     * @param file    文件
     * @param subDir  子目录（如 "file"、"user_avatar/2026-06"）
     * @return 可访问的 URL
     */
    String upload(MultipartFile file, String subDir);

    /**
     * 上传字节数组
     *
     * @param bytes   文件字节
     * @param subDir  子目录
     * @param fileName 文件名
     * @param contentType MIME 类型
     * @return 可访问的 URL
     */
    String upload(byte[] bytes, String subDir, String fileName, String contentType);
}
