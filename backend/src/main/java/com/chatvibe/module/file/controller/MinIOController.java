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
                // 对文本类文件强制追加 UTF-8 字符集，避免浏览器默认 ISO-8859-1 导致中文乱码
                if (contentType.startsWith("text/") && !contentType.toLowerCase().contains("charset")) {
                    contentType = contentType + "; charset=UTF-8";
                }
                // 如果是通用二进制类型，尝试根据扩展名推断更准确的类型
                if ("application/octet-stream".equalsIgnoreCase(contentType)) {
                    String guessed = guessContentType(objectName);
                    if (!"application/octet-stream".equals(guessed)) {
                        contentType = guessed;
                    }
                }
                response.setContentType(contentType);
            } else {
                // MinIO 未返回 Content-Type 时，根据文件扩展名推断
                response.setContentType(guessContentType(objectName));
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

    /**
     * 根据文件扩展名推断 Content-Type，文本类统一追加 charset=UTF-8
     */
    private String guessContentType(String objectName) {
        if (objectName == null) {
            return "application/octet-stream";
        }
        String lower = objectName.toLowerCase();
        if (lower.endsWith(".txt") || lower.endsWith(".log")) {
            return "text/plain; charset=UTF-8";
        }
        if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            return "text/html; charset=UTF-8";
        }
        if (lower.endsWith(".css")) {
            return "text/css; charset=UTF-8";
        }
        if (lower.endsWith(".js")) {
            return "application/javascript; charset=UTF-8";
        }
        if (lower.endsWith(".json")) {
            return "application/json; charset=UTF-8";
        }
        if (lower.endsWith(".xml")) {
            return "application/xml; charset=UTF-8";
        }
        if (lower.endsWith(".csv")) {
            return "text/csv; charset=UTF-8";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        return "application/octet-stream";
    }
}
