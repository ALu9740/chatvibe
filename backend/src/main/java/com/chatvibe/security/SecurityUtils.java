package com.chatvibe.security;

import com.chatvibe.common.result.ResultCode;
import com.chatvibe.common.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全上下文工具类
 * 获取当前登录用户信息
 *
 * @author Alu
 * @date 2026-06-27
 */
public class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 获取当前认证信息
     */
    public static Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    /**
     * 获取当前登录用户
     *
     * @return LoginUser
     * @throws BusinessException 未登录时抛出
     */
    public static LoginUser getCurrentUser() {
        Authentication authentication = getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException(ResultCode.LOGIN_EXPIRED);
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof LoginUser loginUser) {
            return loginUser;
        }
        throw new BusinessException(ResultCode.LOGIN_EXPIRED);
    }

    /**
     * 获取当前用户ID
     */
    public static Long getCurrentUserId() {
        return getCurrentUser().getId();
    }

    /**
     * 获取当前用户邮箱(即登录账号)
     */
    public static String getCurrentEmail() {
        return getCurrentUser().getUsername();
    }

    /**
     * 是否已登录
     */
    public static boolean isAuthenticated() {
        Authentication authentication = getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof LoginUser;
    }
}
