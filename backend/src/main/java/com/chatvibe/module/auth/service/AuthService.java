package com.chatvibe.module.auth.service;

import com.chatvibe.module.auth.dto.LoginDTO;
import com.chatvibe.module.auth.dto.RegisterDTO;
import com.chatvibe.module.auth.dto.ResetPasswordDTO;
import com.chatvibe.module.auth.dto.SendCodeDTO;
import com.chatvibe.module.auth.vo.LoginVO;

/**
 * 认证服务接口
 *
 * @author Alu
 * @date 2026-06-27
 */
public interface AuthService {

    /**
     * 注册
     *
     * @param dto 注册信息
     * @return 登录信息
     */
    LoginVO register(RegisterDTO dto);

    /**
     * 登录
     *
     * @param dto 登录信息
     * @return 登录信息
     */
    LoginVO login(LoginDTO dto);

    /**
     * 发送验证码
     *
     * @param dto 发送验证码信息
     */
    void sendCode(SendCodeDTO dto);

    /**
     * 校验验证码
     *
     * @param email 邮箱
     * @param code  验证码
     * @return 是否校验通过
     */
    boolean verifyCode(String email, String code);

    /**
     * 重置密码
     *
     * @param dto 重置密码信息
     */
    void resetPassword(ResetPasswordDTO dto);

    /**
     * 登出
     */
    void logout();

    /**
     * 刷新 Token
     *
     * @param refreshToken 刷新Token
     */
    LoginVO refresh(String refreshToken);
}
