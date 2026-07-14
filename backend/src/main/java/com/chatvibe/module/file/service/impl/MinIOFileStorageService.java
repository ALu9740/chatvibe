package com.chatvibe.module.file.service.impl;

import com.chatvibe.module.file.service.FileStorageService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.UUID;

/**
 * MinIO 文件存储实现
 *
 * @author Alu
 * @date 2026-07-14
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinIOFileStorageService implements FileStorageService {

    private final MinioClient minioClient;

    @Value("${chatvibe.minio.bucket}")
    private String bucket;

    @Value("${chatvibe.minio.url-prefix:/minio}")
    private String urlPrefix;

    @Override
    public String upload(MultipartFile file, String subDir) {
        try {
            String originalName = file.getOriginalFilename();
            String ext = parseExtension(originalName);
            String uuid = UUID.randomUUID().toString().replace("-", "");
            String objectName = subDir + "/" + uuid + (ext.isEmpty() ? "" : "." + ext);

            try (InputStream inputStream = file.getInputStream()) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .stream(inputStream, file.getSize(), -1)
                        .contentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                        .build());
            }

            String url = buildUrl(objectName);
            log.info("[MinIO] 文件上传成功: orig={}, object={}, size={}", originalName, objectName, file.getSize());
            return url;
        } catch (Exception e) {
            log.error("[MinIO] 文件上传失败: {}", e.getMessage(), e);
            throw new RuntimeException("文件上传失败", e);
        }
    }

    @Override
    public String upload(byte[] bytes, String subDir, String fileName, String contentType) {
        try {
            String objectName = subDir + "/" + fileName;
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                    .contentType(contentType != null ? contentType : "application/octet-stream")
                    .build());
            String url = buildUrl(objectName);
            log.info("[MinIO] 字节上传成功: object={}, size={}", objectName, bytes.length);
            return url;
        } catch (Exception e) {
            log.error("[MinIO] 字节上传失败: {}", e.getMessage(), e);
            throw new RuntimeException("文件上传失败", e);
        }
    }

    private String buildUrl(String objectName) {
        String prefix = urlPrefix.endsWith("/") ? urlPrefix : urlPrefix + "/";
        return prefix + bucket + "/" + objectName;
    }

    private String parseExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase();
    }
}