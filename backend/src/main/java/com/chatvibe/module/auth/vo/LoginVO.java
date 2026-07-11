package com.chatvibe.module.auth.vo;

import com.chatvibe.module.user.vo.UserVO;
import lombok.Data;

/**
 * 登录返回 VO
 *
 * @author Alu
 * @date 2026-06-27
 */
@Data
public class LoginVO {

    /**
     * 访问 Token
     */
    private String accessToken;

    /**
     * 刷新 Token
     */
    private String refreshToken;

    /**
     * Token 类型
     */
    private String tokenType = "Bearer";

    /**
     * 过期时间(毫秒)
     */
    private Long expiresIn;

    /**
     * 用户信息
     */
    private UserVO user;
}
