package com.chatvibe.module.admin.controller;

import com.chatvibe.common.result.PageResult;
import com.chatvibe.common.result.Result;
import com.chatvibe.module.admin.service.AdminGroupService;
import com.chatvibe.module.admin.vo.SystemGroupVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理员群组管理接口
 *
 * @author Alu
 * @date 2026-08-07
 */
@RestController
@RequestMapping("/api/admin/groups")
@RequiredArgsConstructor
public class AdminGroupController {

    private final AdminGroupService adminGroupService;

    /**
     * 分页查询系统群组列表
     */
    @GetMapping
    public Result<PageResult<SystemGroupVO>> getGroupList(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(adminGroupService.getGroupList(keyword, ownerId, status, page, size));
    }

    /**
     * 解散群组
     * 前端以 JSON body { reason: "..." } 发送
     */
    @PostMapping("/{groupId}/dissolve")
    public Result<Boolean> dissolveGroup(@PathVariable Long groupId,
                                          @RequestBody Map<String, String> body) {
        String reason = body.get("reason");
        return Result.success(adminGroupService.dissolveGroup(groupId, reason));
    }
}
