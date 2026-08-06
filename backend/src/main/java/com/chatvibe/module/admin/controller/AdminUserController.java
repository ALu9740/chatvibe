package com.chatvibe.module.admin.controller;

import com.chatvibe.common.result.PageResult;
import com.chatvibe.common.result.Result;
import com.chatvibe.module.admin.dto.BanUserDTO;
import com.chatvibe.module.admin.dto.ChangeRoleDTO;
import com.chatvibe.module.admin.service.AdminUserService;
import com.chatvibe.module.admin.vo.SystemUserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员用户管理接口
 *
 * @author Alu
 * @date 2026-08-06
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    /**
     * 分页查询用户列表
     */
    @GetMapping
    public Result<PageResult<SystemUserVO>> getUserList(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(adminUserService.getUserList(keyword, status, role, page, size));
    }

    /**
     * 封禁用户
     */
    @PostMapping("/{userId}/ban")
    public Result<Boolean> banUser(@PathVariable Long userId, @Valid @RequestBody BanUserDTO dto) {
        return Result.success(adminUserService.banUser(userId, dto.getType(), dto.getDuration(), dto.getReason()));
    }

    /**
     * 解封用户
     */
    @PostMapping("/{userId}/unban")
    public Result<Boolean> unbanUser(@PathVariable Long userId) {
        return Result.success(adminUserService.unbanUser(userId));
    }

    /**
     * 修改用户角色
     */
    @PutMapping("/{userId}/role")
    public Result<Boolean> changeUserRole(@PathVariable Long userId, @Valid @RequestBody ChangeRoleDTO dto) {
        return Result.success(adminUserService.changeUserRole(userId, dto.getRole(), dto.getReason()));
    }

    /**
     * 重置用户密码
     */
    @PostMapping("/{userId}/reset-password")
    public Result<Boolean> resetUserPassword(@PathVariable Long userId) {
        return Result.success(adminUserService.resetUserPassword(userId));
    }
}
