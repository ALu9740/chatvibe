package com.chatvibe.module.file.controller;

import com.chatvibe.common.result.Result;
import com.chatvibe.common.result.ResultCode;
import com.chatvibe.common.exception.BusinessException;
import com.chatvibe.module.file.service.FileStorageService;
import com.chatvibe.module.file.vo.FileUploadVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;


/**
 * 文件上传接口
 * 聊天图片/文件上传到 MinIO 对象存储
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

    private final FileStorageService fileStorageService;

    @PostMapping("/upload")
    public Result<FileUploadVO> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "文件大小不能超过 10MB");
        }

        String url = fileStorageService.upload(file, "file");

        FileUploadVO vo = new FileUploadVO();
        vo.setUrl(url);
        vo.setFileName(file.getOriginalFilename());
        vo.setFileSize(file.getSize());
        vo.setFileType(file.getContentType());
        log.info("[文件] 上传成功: orig={}, size={}", file.getOriginalFilename(), file.getSize());
        return Result.success(vo);
    }
}
