package com.chatvibe.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.chatvibe.common.result.Result;
import com.chatvibe.common.result.ResultCode;
import com.chatvibe.module.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * JWT 认证过滤器
 * 从请求头提取 Token 并校验，校验通过则设置 SecurityContext
 *
 * @author Alu
 * @date 2026-06-27
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = resolveToken(request);
            if (StringUtils.hasText(token) && jwtUtil.validateToken(token)) {
                Claims claims = jwtUtil.parseToken(token);
                String email = claims.getSubject();
                String tokenType = (String) claims.get("type");
                // 仅处理 ACCESS 类型 Token
                if ("ACCESS".equals(tokenType) && StringUtils.hasText(email)) {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                    // 校验登录版本号：不匹配说明账号已在其他设备登录，强制下线
                    if (userDetails instanceof LoginUser loginUser) {
                        Integer tokenVersion = jwtUtil.getLoginVersionFromToken(token);
                        Integer currentVersion = loginUser.getLoginVersion();
                        if (currentVersion != null && !currentVersion.equals(tokenVersion)) {
                            log.info("[JWT] 账号已在其他设备登录，强制下线: userId={}, tokenVersion={}, currentVersion={}",
                                    loginUser.getId(), tokenVersion, currentVersion);
                            // 置用户为离线
                            try {
                                userService.updateStatus(loginUser.getId(), 0);
                            } catch (Exception ignored) {
                            }
                            // 返回 2011 错误，不继续过滤器链
                            writeForceLogoutResponse(response);
                            return;
                        }
                    }

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } else if (StringUtils.hasText(token)) {
                // Token 校验失败：检查是否因过期导致，若是则将用户置为离线
                Long expiredUserId = jwtUtil.getUserIdFromExpiredToken(token);
                if (expiredUserId != null) {
                    try {
                        com.chatvibe.module.user.entity.User u = userService.getById(expiredUserId);
                        // 仅当用户当前非离线时才更新，避免重复广播
                        if (u != null && u.getStatus() != null && u.getStatus() != 0) {
                            userService.updateStatus(expiredUserId, 0);
                            log.info("[JWT] Token 过期，用户已置为离线: userId={}", expiredUserId);
                        }
                    } catch (Exception ex) {
                        log.warn("[JWT] 过期用户状态更新失败: userId={}, err={}", expiredUserId, ex.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[JWT] 认证过滤器异常: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 写入"账号已在其他设备登录"的 401 响应
     */
    private void writeForceLogoutResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Result<Void> result = Result.error(ResultCode.ACCOUNT_LOGIN_ELSEWHERE);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }

    /**
     * 从请求头中解析 Token
     */
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
