package com.chatvibe.module.file.controller;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.GetObjectResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
/**
 * MinIO 接口
 * 将 /minio/** 请求代理到 MinIO 服务器，实现文件读取
 *
 * @author Alu
 * @date 2026-07-15
 */
@Slf4j
@RestController
@RequestMapping("/minio")
@RequiredArgsConstructor
public class MinIOController {

    private final MinioClient minioClient;

    @Value("${chatvibe.minio.bucket}")
    private String bucket;

    @GetMapping("/**")
    public void serveFile(HttpServletRequest request, HttpServletResponse response) {
        // 从 URI 中提取 object 路径：/minio/{bucket}/{object} → {object}
        String uri = request.getRequestURI();
        String prefix = "/minio/" + bucket + "/";
        String objectName;
        if (uri.startsWith(prefix)) {
            objectName = uri.substring(prefix.length());
        } else {
            // 兼容不带 bucket 前缀的情况
            String afterMinio = uri.substring("/minio/".length());
            int slashIdx = afterMinio.indexOf('/');
            objectName = slashIdx > 0 ? afterMinio.substring(slashIdx + 1) : afterMinio;
        }

        try (GetObjectResponse obj = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .build())) {

            String contentType = obj.headers().get("Content-Type");
            if (contentType != null) {
                response.setContentType(contentType);
            }
            String contentLength = obj.headers().get("Content-Length");
            long size = contentLength != null ? Long.parseLong(contentLength) : -1L;
            if (size > 0) {
                response.setContentLengthLong(size);
            }
            obj.transferTo(response.getOutputStream());
        } catch (Exception e) {
            log.warn("[MinIO] 文件读取失败: object={}, err={}", objectName, e.getMessage());
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }
}
