package com.chatvibe.module.file.controller;

import cn.hutool.core.util.StrUtil;
import com.chatvibe.common.result.Result;
import com.chatvibe.common.result.ResultCode;
import com.chatvibe.common.exception.BusinessException;
import com.chatvibe.module.file.vo.FileUploadVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 文件上传接口
 * 聊天图片/文件上传到本地 /uploads/file/{uuid}.ext
 *
 * @author Alu
 * @date 2026-07-01
 */
@Slf4j
@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileController {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024L; // 10MB

    @Value("${chatvibe.upload.base-dir:./uploads}")
    private String baseDir;

    @Value("${chatvibe.upload.url-prefix:/uploads}")
    private String urlPrefix;

    /**
     * 上传文件(聊天图片/文件)
     * 保存路径: {baseDir}/file/{uuid}.{ext}
     * 访问 URL: {urlPrefix}/file/{uuid}.{ext}
     */
    @PostMapping("/upload")
    public Result<FileUploadVO> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "文件大小不能超过 10MB");
        }
        String originalName = file.getOriginalFilename();
        String ext = parseExtension(originalName);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String savedName = uuid + (StrUtil.isNotBlank(ext) ? "." + ext : "");
        Path dirPath = Paths.get(baseDir, "file");
        Path filePath = dirPath.resolve(savedName);
        try {
            Files.createDirectories(dirPath);
            // 使用绝对路径避免 MultipartFile.transferTo() 解析到 Tomcat 临时目录
            file.transferTo(filePath.toAbsolutePath().toFile());
        } catch (IOException e) {
            log.error("[文件] 写入磁盘失败: path={}, msg={}", filePath, e.getMessage());
            throw new BusinessException(ResultCode.FAIL, "文件上传失败，请稍后重试");
        }
        String url = buildUrl("file", savedName);
        FileUploadVO vo = new FileUploadVO();
        vo.setUrl(url);
        vo.setFileName(originalName);
        vo.setFileSize(file.getSize());
        vo.setFileType(file.getContentType());
        log.info("[文件] 上传成功: orig={}, saved={}, size={}", originalName, savedName, file.getSize());
        return Result.success(vo);
    }

    /**
     * 解析文件扩展名(小写)
     */
    private String parseExtension(String fileName) {
        if (StrUtil.isBlank(fileName)) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase();
    }

    /**
     * 拼接可访问 URL
     */
    private String buildUrl(String subDir, String fileName) {
        String prefix = urlPrefix.endsWith("/") ? urlPrefix : urlPrefix + "/";
        return prefix + subDir + "/" + fileName;
    }
}
