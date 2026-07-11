package com.chatvibe.module.user.controller;

import com.chatvibe.common.result.Result;
import com.chatvibe.module.user.dto.ChangeEmailDTO;
import com.chatvibe.module.user.dto.ChangePasswordDTO;
import com.chatvibe.module.user.dto.UpdateProfileDTO;
import com.chatvibe.module.user.service.UserService;
import com.chatvibe.module.user.vo.NotificationPreferencesVO;
import com.chatvibe.module.user.vo.UserVO;
import com.chatvibe.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户接口
 *
 * @author Alu
 * @date 2026-06-28
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 获取当前登录用户资料
     */
    @GetMapping("/me")
    public Result<UserVO> getCurrentUser() {
        return Result.success(userService.getCurrentUserInfo());
    }

    /**
     * 根据ID获取用户资料
     */
    @GetMapping("/{userId}")
    public Result<UserVO> getUserInfo(@PathVariable Long userId) {
        return Result.success(userService.getUserInfo(userId));
    }

    /**
     * 更新当前用户资料
     */
    @PutMapping("/profile")
    public Result<UserVO> updateProfile(@Valid @RequestBody UpdateProfileDTO dto) {
        return Result.success(userService.updateProfile(dto));
    }

    /**
     * 修改密码
     */
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        userService.changePassword(dto);
        return Result.success();
    }

    /**
     * 更换绑定邮箱
     */
    @PutMapping("/email")
    public Result<Boolean> changeEmail(@Valid @RequestBody ChangeEmailDTO dto) {
        userService.changeEmail(dto);
        return Result.success(true);
    }

    /**
     * 头像上传(base64)
     */
    @PostMapping("/avatar")
    public Result<String> uploadAvatar(@RequestBody Map<String, String> body) {
        String base64 = body.get("base64");
        return Result.success(userService.uploadAvatar(base64));
    }

    /**
     * 获取通知偏好
     */
    @GetMapping("/notifications")
    public Result<NotificationPreferencesVO> getNotificationPreferences() {
        return Result.success(userService.getNotificationPreferences());
    }

    /**
     * 更新通知偏好
     */
    @PutMapping("/notifications")
    public Result<Boolean> updateNotificationPreferences(@RequestBody NotificationPreferencesVO vo) {
        userService.updateNotificationPreferences(vo);
        return Result.success(true);
    }

    /**
     * 更新当前用户在线状态
     */
    @PutMapping("/status")
    public Result<Integer> updateStatus(@RequestParam Integer status) {
        Long userId = SecurityUtils.getCurrentUserId();
        userService.updateStatusManually(userId, status);
        return Result.success(status);
    }
}
