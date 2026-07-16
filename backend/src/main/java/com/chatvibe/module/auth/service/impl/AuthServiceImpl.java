package com.chatvibe.module.auth.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chatvibe.common.result.ResultCode;
import com.chatvibe.common.exception.BusinessException;
import com.chatvibe.module.auth.dto.LoginDTO;
import com.chatvibe.module.auth.dto.RegisterDTO;
import com.chatvibe.module.auth.dto.ResetPasswordDTO;
import com.chatvibe.module.auth.dto.SendCodeDTO;
import com.chatvibe.module.auth.event.*;
import com.chatvibe.module.auth.service.AuthService;
import com.chatvibe.module.auth.vo.LoginVO;
import com.chatvibe.module.chat.service.ChatService;
import com.chatvibe.module.user.entity.User;
import com.chatvibe.module.user.enums.UserRoleEnum;
import com.chatvibe.module.user.enums.UserStatusEnum;
import com.chatvibe.module.user.mapper.UserMapper;
import com.chatvibe.module.user.service.UserService;
import com.chatvibe.security.JwtUtil;
import com.chatvibe.security.SecurityUtils;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;

/**
 * 认证服务实现
 *
 * @author Alu
 * @since 2026-06-27
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String CODE_KEY_PREFIX = "code:verify:";
    private static final String LIMIT_KEY_PREFIX = "code:verify:limit:";
    private static final String LOGIN_FAIL_PREFIX = "login:fail:";
    private static final String LOGIN_LOCK_PREFIX = "login:lock:";
    private static final int LOGIN_MAX_FAIL = 5;
    private static final Duration LOGIN_FAIL_TTL = Duration.ofMinutes(15);
    private static final Duration LOGIN_LOCK_TTL = Duration.ofMinutes(15);
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration LIMIT_TTL = Duration.ofSeconds(60);


    private final UserMapper userMapper;
    private final UserService userService;
    private final ChatService chatService;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate stringRedisTemplate;
    private final JavaMailSender mailSender;
    private final CacheManager cacheManager;
    private final UserRegisterEventProducer userRegisterEventProducer;
    private final UserLoginEventProducer userLoginEventProducer;
    private final UserPasswordResetEventProducer userPasswordResetEventProducer;
    private final UserLogoutEventProducer userLogoutEventProducer;

    @Value("${spring.mail.username}")
    private String mailFrom;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO register(RegisterDTO dto) {
        // 校验验证码
        if (!verifyCode(dto.getEmail(), dto.getCode())) {
            throw new BusinessException(ResultCode.CODE_NOT_MATCH);
        }
        // 校验邮箱是否已存在(从缓存中获取)
        Cache emailCache = cacheManager.getCache("emailExists");
        if(emailCache != null){
            Boolean cacheExist = emailCache.get(dto.getEmail(), Boolean.class);
            if(Boolean.TRUE.equals(cacheExist)){
                throw new BusinessException(ResultCode.EMAIL_EXISTS);
            }
        }

        // 缓存未命中，再查DB
        Long existCount = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, dto.getEmail()));
        if (existCount > 0){
            // 写入缓存，5 分钟内同一邮箱再次注册直接命中
            if (emailCache != null){
                emailCache.put(dto.getEmail(),true);
            }
            throw new BusinessException(ResultCode.EMAIL_EXISTS);
        }
        // 创建用户
        User user = new User();
        user.setEmail(dto.getEmail());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        // 默认昵称取邮箱 @ 前缀
        user.setNickname(dto.getEmail().substring(0, dto.getEmail().indexOf('@')));
        user.setStatus(UserStatusEnum.OFFLINE.getCode());
        user.setRole(UserRoleEnum.USER.getCode());
        userMapper.insert(user);
        log.info("[注册] 新用户注册成功: userId={}, email={}", user.getId(), user.getEmail());

        // 异步创建AI会话
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        userRegisterEventProducer.sendRegisterEvent(
                                new UserRegisterEvent(user.getId(), user.getEmail(), user.getNickname())
                        );
                    }
                }
        );

        // 删除已使用的验证码
        stringRedisTemplate.delete(CODE_KEY_PREFIX + dto.getEmail());

        // 生成 Token 并返回
        return buildLoginVO(user);
    }

    @Override
    @RateLimiter(name = "loginRateLimiter", fallbackMethod = "loginRateLimitFallback")
    public LoginVO login(LoginDTO dto) {
        String email = dto.getEmail();

        // 1. 账号锁定检查（Redis 防爆破）
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(LOGIN_LOCK_PREFIX + email))) {
            throw new BusinessException(ResultCode.ACCOUNT_LOCKED);
        }

        // 2. 查询用户
        User user = userService.findByEmail(email);

        // 3. 密码校验；失败则计数/锁定
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            recordLoginFailure(email);
            throw new BusinessException(ResultCode.EMAIL_OR_PASSWORD_ERROR);
        }

        // 4. 登录成功，清除失败计数
        stringRedisTemplate.delete(LOGIN_FAIL_PREFIX + email);

        // 5. 原子递增 loginVersion + 置在线（修复读改写竞态，规避缓存陈旧）
        userMapper.incrLoginVersionAndSetOnline(user.getId(), UserStatusEnum.ONLINE.getCode());
        // 同步 Redis 状态 + 清除 Caffeine 缓存，避免 Redis 残留旧离线值导致 getUserStatus 返回陈旧数据
        userService.syncStatusToRedis(user.getId(), UserStatusEnum.ONLINE.getCode());
        Integer newVersion = userMapper.selectLoginVersion(user.getId());
        user.setLoginVersion(newVersion);
        user.setStatus(UserStatusEnum.ONLINE.getCode());

        // 6. 异步发送强制下线通知（MQ 解耦，不再阻塞登录响应）
        userLoginEventProducer.sendLoginEvent(
                new UserLoginEvent(user.getId(), user.getEmail(), newVersion, System.currentTimeMillis()));

        log.info("[登录] 用户登录成功: userId={}, email={}", user.getId(), email);
        return buildLoginVO(user);
    }

    @Override
    public void sendCode(SendCodeDTO dto) {
        String email = dto.getEmail();
        // 60秒限流
        String limitKey = LIMIT_KEY_PREFIX + email;
        Boolean exists = stringRedisTemplate.hasKey(limitKey);
        if (Boolean.TRUE.equals(exists)) {
            throw new BusinessException(ResultCode.CODE_SEND_TOO_FREQUENT);
        }
        // 生成6位验证码
        String code = RandomUtil.randomNumbers(6);
        // 先存验证码到 Redis
        stringRedisTemplate.opsForValue().set(CODE_KEY_PREFIX + email, code, CODE_TTL);
        // 先发邮件，成功后再写限流标记（避免邮件失败后用户 60s 内无法重试）
        sendVerificationEmail(email, code);
        stringRedisTemplate.opsForValue().set(limitKey, "1", LIMIT_TTL);
        log.info("[验证码] 验证码已发送: email={}", email);
    }

    @Override
    public boolean verifyCode(String email, String code) {
        if (StrUtil.hasBlank(email, code)) {
            return false;
        }
        String cached = stringRedisTemplate.opsForValue().get(CODE_KEY_PREFIX + email);
        if (cached == null) {
            throw new BusinessException(ResultCode.CODE_INVALID);
        }
        return cached.equals(code);
    }

    @Override
    @RateLimiter(name = "resetPasswordRateLimiter", fallbackMethod = "resetPasswordRateLimitFallback")
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(ResetPasswordDTO dto) {
        // 1. 校验验证码
        if (!verifyCode(dto.getEmail(), dto.getCode())) {
            throw new BusinessException(ResultCode.CODE_NOT_MATCH);
        }
        // 2. 校验邮箱存在
        User user = userService.findByEmail(dto.getEmail());
        if (user == null) {
            throw new BusinessException(ResultCode.EMAIL_NOT_FOUND);
        }

        // 3. 更新密码
        userMapper.update(new LambdaUpdateWrapper<User>()
                .eq(User::getId, user.getId())
                .set(User::getPassword, passwordEncoder.encode(dto.getPassword())));

        // 4. 递增 loginVersion + 置离线 -> 使旧 Token 立即失效
        userMapper.incrLoginVersionAndSetOnline(user.getId(), UserStatusEnum.OFFLINE.getCode());

        // 5. 同步 Redis 状态 + 清除 Caffeine 缓存
        userService.syncStatusToRedis(user.getId(), UserStatusEnum.OFFLINE.getCode());

        // 6. 删除已使用的验证码
        stringRedisTemplate.delete(CODE_KEY_PREFIX + dto.getEmail());

        // 7. 事务提交后异步发送"密码已重置"通知邮件（MQ 解耦，不阻塞响应）
        final Long userId = user.getId();
        final String email = user.getEmail();
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        userPasswordResetEventProducer.sendPasswordResetEvent(
                                new UserPasswordResetEvent(userId, email, System.currentTimeMillis()));
                    }
                }
        );

        log.info("[重置密码] 用户密码已重置: userId={}, email={}", user.getId(), user.getEmail());
    }

    @Override
    public void logout() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            log.warn("[登出] 无法获取当前用户ID");
            return;
        }

        // 1. 递增 loginVersion + 置离线 -> 使旧 Token 真正失效
        userMapper.incrLoginVersionAndSetOnline(userId, UserStatusEnum.OFFLINE.getCode());

        // 2. 同步 Redis 状态 + 清除 Caffeine 缓存
        userService.syncStatusToRedis(userId, UserStatusEnum.OFFLINE.getCode());

        // 3. 通过 MQ 异步广播离线状态（不阻塞登出响应）
        userLogoutEventProducer.sendLogoutEvent(
                new UserLogoutEvent(userId, System.currentTimeMillis()));

        log.info("[登出] 用户登出成功: userId={}", userId);
    }

    @Override
    public LoginVO refresh(String refreshToken) {
        if (!jwtUtil.validateToken(refreshToken)) {
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }
        String email = jwtUtil.getEmailFromToken(refreshToken);
        Long userId = jwtUtil.getUserIdFromToken(refreshToken);
        User user = userService.getById(userId);
        if (user == null || !email.equals(user.getEmail())) {
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }
        // 校验登录版本号：不匹配说明账号已在其他设备登录
        Integer tokenVersion = jwtUtil.getLoginVersionFromToken(refreshToken);
        Integer currentVersion = user.getLoginVersion() != null ? user.getLoginVersion() : 0;
        if (!currentVersion.equals(tokenVersion)) {
            throw new BusinessException(ResultCode.ACCOUNT_LOGIN_ELSEWHERE);
        }
        return buildLoginVO(user);
    }

    /**
     * 构建登录返回对象
     */
    private LoginVO buildLoginVO(User user) {
        Integer loginVersion = user.getLoginVersion() != null ? user.getLoginVersion() : 0;
        LoginVO vo = new LoginVO();
        vo.setAccessToken(jwtUtil.generateAccessToken(user.getId(), user.getEmail(), loginVersion));
        vo.setRefreshToken(jwtUtil.generateRefreshToken(user.getId(), user.getEmail(), loginVersion));
        vo.setExpiresIn(jwtUtil.getAccessTokenExpiration() / 1000);
        vo.setUser(userService.toVO(user));
        return vo;
    }

    /**
     * 发送验证码邮件(HTML 模板)
     */
    private void sendVerificationEmail(String email, String code) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(mailFrom);
            helper.setTo(email);
            helper.setSubject("【ChatVibe】邮箱验证码");
            String html = buildEmailHtml(code);
            helper.setText(html, true);
            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
            log.error("[邮件] 验证码邮件发送失败: email={}", email, e);
            throw new BusinessException(ResultCode.FAIL, "验证码邮件发送失败，请稍后重试");
        }
    }

    /**
     * 构建邮件 HTML 内容
     */
    private String buildEmailHtml(String code) {
        return """
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"></head>
                <body style="font-family: 'Helvetica Neue', Arial, sans-serif; background:#F7F9FC; padding:24px;">
                  <div style="max-width:480px; margin:0 auto; background:#fff; border-radius:16px; overflow:hidden; box-shadow:0 8px 24px rgba(15,23,42,0.08);">
                    <div style="background:linear-gradient(135deg,#1E40AF 0%%,#2563EB 45%%,#0EA5E9 100%%); padding:32px 24px; text-align:center;">
                      <h1 style="color:#fff; margin:0; font-size:24px; letter-spacing:0.5px;">ChatVibe</h1>
                      <p style="color:rgba(255,255,255,0.85); font-size:12px; margin:6px 0 0;">让沟通更有温度</p>
                    </div>
                    <div style="padding:32px 24px;">
                      <h2 style="color:#0F172A; font-size:18px; margin:0 0 16px;">邮箱验证码</h2>
                      <p style="color:#475569; font-size:14px; line-height:1.6;">您好，您正在进行身份验证，请使用以下验证码完成操作：</p>
                      <div style="margin:24px 0; text-align:center;">
                        <span style="display:inline-block; font-size:32px; font-weight:bold; letter-spacing:8px; color:#2563EB; background:#EFF6FF; padding:16px 32px; border-radius:8px; border:1px solid #DBEAFE;">%s</span>
                      </div>
                      <p style="color:#999; font-size:12px; line-height:1.6;">验证码有效期为 5 分钟，请尽快使用。如非本人操作，请忽略此邮件。</p>
                    </div>
                    <div style="background:#fafafa; padding:16px 24px; text-align:center; border-top:1px solid #eee;">
                      <p style="color:#bbb; font-size:12px; margin:0;">© ChatVibe · 本邮件由系统自动发送，请勿回复</p>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(code);
    }

    /**
     * 记录登录失败次数，超阈值则锁定账号
     */
    private void recordLoginFailure(String email) {
        String failKey = LOGIN_FAIL_PREFIX + email;
        Long count = stringRedisTemplate.opsForValue().increment(failKey);
        if (count != null && count == 1) {
            stringRedisTemplate.expire(failKey, LOGIN_FAIL_TTL);
        }
        if (count != null && count >= LOGIN_MAX_FAIL) {
            stringRedisTemplate.opsForValue().set(LOGIN_LOCK_PREFIX + email, "1", LOGIN_LOCK_TTL);
            stringRedisTemplate.delete(failKey);
            log.warn("[登录] 账号多次失败被锁定: email={}, count={}", email, count);
        }
    }

    /** 限流降级：直接返回"请求过于频繁" */
    private LoginVO loginRateLimitFallback(LoginDTO dto, Throwable t) {
        throw new BusinessException(ResultCode.TOO_MANY_REQUESTS);
    }


    /** 限流降级 */
    private void resetPasswordRateLimitFallback(ResetPasswordDTO dto, Throwable t) {
        throw new BusinessException(ResultCode.TOO_MANY_REQUESTS);
    }
}