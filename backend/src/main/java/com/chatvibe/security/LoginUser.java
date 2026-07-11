package com.chatvibe.security;

import com.chatvibe.module.user.entity.User;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * 登录用户信息封装
 * 实现 Spring Security 的 UserDetails
 *
 * @author Alu
 * @date 2026-06-27
 */
@Data
public class LoginUser implements UserDetails {

    private Long id;
    /** 邮箱(即登录账号，作为 Spring Security 的 principal) */
    private String email;
    private String password;
    private String nickname;
    private String avatar;
    private String role;
    private boolean enabled;
    /** 登录版本号（用于多设备登录冲突检测） */
    private Integer loginVersion;

    public LoginUser() {
    }

    public LoginUser(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.password = user.getPassword();
        this.nickname = user.getNickname();
        this.avatar = user.getAvatar();
        this.role = user.getRole();
        this.enabled = true;
        this.loginVersion = user.getLoginVersion() != null ? user.getLoginVersion() : 0;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String authority = "ROLE_" + (role == null ? "USER" : role.toUpperCase());
        return Collections.singletonList(new SimpleGrantedAuthority(authority));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        // Spring Security 要求实现 getUsername()，此处返回邮箱作为唯一标识
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
