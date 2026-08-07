package com.chatvibe.module.admin.controller;

import com.chatvibe.common.result.PageResult;
import com.chatvibe.common.result.Result;
import com.chatvibe.module.admin.dto.CreateAnnouncementDTO;
import com.chatvibe.module.admin.service.AdminAnnouncementService;
import com.chatvibe.module.admin.vo.AnnouncementVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员公告接口
 *
 * @author Alu
 * @date 2026-08-07
 */
@RestController
@RequestMapping("/api/admin/announcements")
@RequiredArgsConstructor
public class AdminAnnouncementController {

    private final AdminAnnouncementService adminAnnouncementService;

    /**
     * 分页查询公告列表（支持标题搜索）
     *
     * @param keyword 搜索关键词(可选，模糊匹配标题)
     * @param page    页码(默认1)
     * @param size    每页大小(默认20)
     * @return 公告分页列表
     */
    @GetMapping
    public Result<PageResult<AnnouncementVO>> getAnnouncementList(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(adminAnnouncementService.getAnnouncementList(keyword, page, size));
    }

    /**
     * 创建公告
     *
     * @param dto 公告信息
     * @return 创建结果
     */
    @PostMapping
    public Result<Boolean> createAnnouncement(@Valid @RequestBody CreateAnnouncementDTO dto) {
        return Result.success(adminAnnouncementService.createAnnouncement(dto));
    }

    /**
     * 撤回公告
     *
     * @param id 公告ID
     * @return 撤回结果
     */
    @PostMapping("/{id}/withdraw")
    public Result<Boolean> withdrawAnnouncement(@PathVariable Long id) {
        return Result.success(adminAnnouncementService.withdrawAnnouncement(id));
    }
}
