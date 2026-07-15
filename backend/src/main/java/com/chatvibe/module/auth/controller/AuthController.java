package com.chatvibe.module.auth.controller;

import com.chatvibe.common.exception.BusinessException;
import com.chatvibe.common.result.Result;
import com.chatvibe.common.result.ResultCode;
import com.chatvibe.module.auth.dto.LoginDTO;
import com.chatvibe.module.auth.dto.RegisterDTO;
import com.chatvibe.module.auth.dto.ResetPasswordDTO;
import com.chatvibe.module.auth.dto.SendCodeDTO;
import com.chatvibe.module.auth.service.AuthService;
import com.chatvibe.module.auth.vo.LoginVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

/**
 * 认证接口
 *
 * @author Alu
 * @date 2026-06-27
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REGISTER_LIMIT_PREFIX = "limit:register:";
    private static final Duration REGISTER_LIMIT_TTL = Duration.ofHours(1);
    private static final int REGISTER_MAX_COUNT = 5;

    private final AuthService authService;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterDTO dto, HttpServletRequest request) {
        // IP 限流
        String ip = getClientIp(request);
        String limitKey = REGISTER_LIMIT_PREFIX + ip;
        Long count = stringRedisTemplate.opsForValue().increment(limitKey);
        if (count != null && count == 1) {
            stringRedisTemplate.expire(limitKey, REGISTER_LIMIT_TTL);
        }
        if (count != null && count > REGISTER_MAX_COUNT) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS);
        }

        return Result.success(authService.register(dto));
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success(authService.login(dto));
    }

    /**
     * 发送验证码
     */
    @PostMapping("/code")
    public Result<Void> sendCode(@Valid @RequestBody SendCodeDTO dto) {
        authService.sendCode(dto);
        return Result.success("验证码已发送", null);
    }

    /**
     * 校验验证码
     */
    @GetMapping("/code/verify")
    public Result<Boolean> verifyCode(@RequestParam String email, @RequestParam String code) {
        return Result.success(authService.verifyCode(email, code));
    }

    /**
     * 重置密码
     */
    @PostMapping("/password/reset")
    public Result<Boolean> resetPassword(@Valid @RequestBody ResetPasswordDTO dto) {
        authService.resetPassword(dto);
        return Result.success(true);
    }

    /**
     * 登出
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.success();
    }

    /**
     * 刷新 Token
     */
    @PostMapping("/refresh")
    public Result<LoginVO> refresh(@RequestHeader("Authorization") String refreshToken) {
        // 去除 Bearer 前缀
        String token = refreshToken.startsWith("Bearer ") ? refreshToken.substring(7) : refreshToken;
        return Result.success(authService.refresh(token));
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多层代理取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
