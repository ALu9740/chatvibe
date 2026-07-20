package com.chatvibe.module.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chatvibe.common.result.ResultCode;
import com.chatvibe.common.exception.BusinessException;
import com.chatvibe.module.file.service.FileStorageService;
import com.chatvibe.module.user.dto.ChangeEmailDTO;
import com.chatvibe.module.user.dto.ChangePasswordDTO;
import com.chatvibe.module.user.dto.UpdateProfileDTO;
import com.chatvibe.module.user.entity.User;
import com.chatvibe.module.user.enums.UserAvatarEnum;
import com.chatvibe.module.user.enums.UserStatusEnum;
import com.chatvibe.module.user.mapper.UserMapper;
import com.chatvibe.module.user.service.UserService;
import com.chatvibe.module.user.vo.NotificationPreferencesVO;
import com.chatvibe.module.user.vo.UserVO;
import com.chatvibe.security.SecurityUtils;
import com.chatvibe.websocket.dto.WsStatusMessage;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 用户服务实现
 *
 * @author Alu
 * @date 2026-06-28
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final String CODE_KEY_PREFIX = "code:verify:";

    // 用户状态 Redis Key 前缀
    private static final String USER_STATUS_KEY_PREFIX = "user:status:";
    private static final Duration STATUS_TTL = Duration.ofMinutes(10);

    // 自注入代理，解决 @Cacheable 自调用失效问题
    @Lazy
    @Autowired
    private UserService self;

    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate stringRedisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final CacheManager cacheManager;

    private final FileStorageService fileStorageService;

    @Override
    public Integer getUserStatus(Long userId) {
        // 1. 先查 Redis
        String statusStr = stringRedisTemplate
                .opsForValue()
                .get(USER_STATUS_KEY_PREFIX + userId);
        if (statusStr != null) {
            return Integer.parseInt(statusStr);
        }
        // 2. Redis 未命中，查 DB 并回写
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        Integer status = user.getStatus() != null ? user.getStatus() : UserStatusEnum.OFFLINE.getCode();
        stringRedisTemplate
                .opsForValue()
                .set(USER_STATUS_KEY_PREFIX + userId, status.toString(), STATUS_TTL);
        return status;
    }

    @Override
    public UserVO getCurrentUserInfo() {
        Long userId = SecurityUtils.getCurrentUserId();
        return self.getUserInfo(userId);
    }

    @Override
    @Cacheable(value = "userInfo", key = "#userId")
    @RateLimiter(name = "userInfoRateLimiter", fallbackMethod = "getUserInfoRateLimitFallback")
    public UserVO getUserInfo(Long userId) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        UserVO vo = toVO(user);
        vo.setStatus(null);
        return vo;
    }




    @Override
    public UserVO updateProfile(UpdateProfileDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (StrUtil.isNotBlank(dto.getNickname())) {
            user.setNickname(dto.getNickname());
        }
        if (StrUtil.isNotBlank(dto.getAvatar())) {
            user.setAvatar(dto.getAvatar());
        }
        if (dto.getBio() != null) {
            user.setBio(dto.getBio());
        }
        updateById(user);
        evictUserInfoCache(userId);
        Cache searchCache = cacheManager.getCache("userSearch");
        if (searchCache != null) {
            searchCache.clear();
        }
        return toVO(user);
    }

    @Override
    @RateLimiter(name = "changePasswordRateLimiter", fallbackMethod = "changePasswordRateLimitFallback")
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(ChangePasswordDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.OLD_PASSWORD_ERROR);
        }
        // 只更新 password 字段
        update(new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getPassword, passwordEncoder.encode(dto.getNewPassword())));
        // 递增 loginVersion + 置离线 → 使所有旧 Token 立即失效
        baseMapper.incrLoginVersionAndSetOnline(userId, UserStatusEnum.OFFLINE.getCode());
        // 同步更新 Redis 状态
        stringRedisTemplate.opsForValue()
                .set(USER_STATUS_KEY_PREFIX + userId, "0", STATUS_TTL);
        // 清除 Caffeine 缓存
        evictUserInfoCache(userId);
        log.info("[用户] 修改密码成功, userId={}", userId);
    }

    @Override
    @RateLimiter(name = "uploadAvatarRateLimiter", fallbackMethod = "uploadAvatarRateLimitFallback")
    @Transactional(rollbackFor = Exception.class)
    public String uploadAvatar(String base64) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (StrUtil.isBlank(base64)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, UserAvatarEnum.AVATAR_DATA_EMPTY.getMessage());
        }
        // 去除 data URI 前缀: data:image/png;base64,xxxx
        String data = base64;
        String ext = "png";
        int commaIdx = base64.indexOf(',');
        if (commaIdx > 0 && base64.startsWith("data:")) {
            String header = base64.substring(0, commaIdx);
            data = base64.substring(commaIdx + 1);
            // 解析图片类型
            if (header.contains("image/jpeg") || header.contains("image/jpg")) {
                ext = "jpg";
            } else if (header.contains("image/gif")) {
                ext = "gif";
            } else if (header.contains("image/webp")) {
                ext = "webp";
            }
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.PARAM_INVALID, UserAvatarEnum.AVATAR_BASE64_DECODE_FAILED.getMessage());
        }
        if (bytes.length > 2 * 1024 * 1024) {
            throw new BusinessException(ResultCode.PARAM_INVALID, UserAvatarEnum.AVATAR_SIZE_EXCEEDED.getMessage());
        }
        // 上传到 MinIO
        String monthDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        String subDir = "user_avatar/" + monthDir;
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String savedName = uuid + "." + ext;
        String contentType = "image/" + ext;
        String url = fileStorageService.upload(bytes, subDir, savedName, contentType);

        update(new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getAvatar, url));
        log.info("[用户] 头像上传成功: userId={}, url={}", userId, url);
        evictUserInfoCache(userId);
        Cache searchCache = cacheManager.getCache("userSearch");
        if (searchCache != null) {
            searchCache.clear();
        }
        return url;
    }

    @Override
    @RateLimiter(name = "searchRateLimiter", fallbackMethod = "searchFallback")
    @CircuitBreaker(name = "searchService", fallbackMethod = "searchFallback")
    public List<UserVO> searchUsers(String keyword) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        // 1. 关键字校验：trim + 长度限制，空串直接返回空列表
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty() || kw.length() > 255) {
            return Collections.emptyList();
        }
        // 2. 查 Caffeine 缓存（命中直接返回，未命中自动查库并回写）
        Cache searchCache = cacheManager.getCache("userSearch");
        List<User> users;
        if (searchCache != null) {
            users = searchCache.get(kw, () -> baseMapper.searchUsers(kw));
        } else {
            users = baseMapper.searchUsers(kw);
        }
        // 3. 内存中过滤自身 + 转 VO
        return users.stream()
                .filter(u -> !u.getId().equals(currentUserId))
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public User findByEmail(String email) {
        return baseMapper.findByEmail(email);
    }

    @Override
    public UserVO toVO(User user) {
        UserVO vo = new UserVO();
        BeanUtil.copyProperties(user, vo);
        return vo;
    }

    @Override
    public void updateStatus(Long userId, Integer status) {
        if (!UserStatusEnum.isValid(status)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "状态值无效");
        }
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (status.equals(user.getStatus())) {
            return;
        }
        // 1. 更新 DB
        update(new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getStatus, status));
        // 2. 同步写 Redis（实时状态）
        stringRedisTemplate
                .opsForValue()
                .set(USER_STATUS_KEY_PREFIX + userId, status.toString(), STATUS_TTL);
        // 3. 清除 Caffeine 缓存
        evictUserInfoCache(userId);
        // 4. 广播状态变更
        broadcastStatus(userId, status);
    }

    @Override
    public void updateStatusManually(Long userId, Integer status) {
        updateStatus(userId, status);
    }

    @Override
    public void syncStatusToRedis(Long userId, Integer status) {
        stringRedisTemplate
                .opsForValue()
                .set(USER_STATUS_KEY_PREFIX + userId, status.toString(), STATUS_TTL);
        evictUserInfoCache(userId);
    }

    private void broadcastStatus(Long userId, Integer status) {
        try {
            messagingTemplate.convertAndSend("/topic/status", new WsStatusMessage(userId, status));
        } catch (Exception e) {
            log.warn("[用户] 状态变更广播失败: userId={}, status={}, err={}", userId, status, e.getMessage());
        }
    }

    @Override
    @RateLimiter(name = "changeEmailRateLimiter", fallbackMethod = "changeEmailRateLimitFallback")
    @Transactional(rollbackFor = Exception.class)
    public void changeEmail(ChangeEmailDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        String newEmail = dto.getNewEmail();
        // 校验新邮箱未被使用
        Long existCount = baseMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, newEmail));
        if (existCount > 0) {
            throw new BusinessException(ResultCode.EMAIL_EXISTS);
        }
        // 校验验证码（与新邮箱匹配）
        String cached = stringRedisTemplate.opsForValue().get(CODE_KEY_PREFIX + newEmail);
        if (cached == null) {
            throw new BusinessException(ResultCode.CODE_INVALID);
        }
        if (!cached.equals(dto.getCode())) {
            throw new BusinessException(ResultCode.CODE_NOT_MATCH);
        }
        // 更新邮箱
        update(new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getEmail, newEmail));
        // 删除已用验证码（防重放）
        stringRedisTemplate.delete(CODE_KEY_PREFIX + newEmail);
        // 清除 emailExists 缓存（旧邮箱的缓存标记需要清除）
        Cache emailCache = cacheManager.getCache("emailExists");
        if (emailCache != null) {
            emailCache.evict(dto.getNewEmail());
        }
        // 邮箱是 JWT 身份凭证的一部分，更换后旧 Token 立即失效
        updateStatus(userId, 0);
        log.info("[用户] 更换邮箱成功: userId={}, newEmail={}", userId, newEmail);
    }

    @Override
    @Cacheable(value = "userNotifyPrefs", key = "#userId")
    public NotificationPreferencesVO getNotificationPreferences(Long userId) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        NotificationPreferencesVO vo = new NotificationPreferencesVO();
        vo.setDesktop(toBool(user.getNotifyDesktop(), true));
        vo.setSound(toBool(user.getNotifySound(), true));
        vo.setAiAlert(toBool(user.getNotifyAiAlert(), false));
        return vo;
    }

    @Override
    public NotificationPreferencesVO getNotificationPreferences() {
        Long userId = SecurityUtils.getCurrentUserId();
        return self.getNotificationPreferences(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateNotificationPreferences(NotificationPreferencesVO vo) {
        Long userId = SecurityUtils.getCurrentUserId();
        LambdaUpdateWrapper<User> wrapper = new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId);
        boolean hasUpdate = false;
        if (vo.getDesktop() != null) {
            wrapper.set(User::getNotifyDesktop, vo.getDesktop() ? 1 : 0);
            hasUpdate = true;
        }
        if (vo.getSound() != null) {
            wrapper.set(User::getNotifySound, vo.getSound() ? 1 : 0);
            hasUpdate = true;
        }
        if (vo.getAiAlert() != null) {
            wrapper.set(User::getNotifyAiAlert, vo.getAiAlert() ? 1 : 0);
            hasUpdate = true;
        }
        if (!hasUpdate) {
            return;
        }
        update(wrapper);
        // 清除通知偏好缓存
        Cache notifyCache = cacheManager.getCache("userNotifyPrefs");
        if (notifyCache != null) {
            notifyCache.evict(userId);
        }
        // 同步清除 userInfo 缓存
        evictUserInfoCache(userId);
        log.info("[用户] 通知偏好已更新: userId={}", userId);
    }

    /**
     * Integer 0/1 → Boolean，null 时返回默认值
     */
    private Boolean toBool(Integer val, boolean defaultVal) {
        if (val == null) {
            return defaultVal;
        }
        return val == 1;
    }

    /**
     * 清除用户信息 Caffeine 缓存
     */
    private void evictUserInfoCache(Long userId) {
        Cache cache = cacheManager.getCache("userInfo");
        if (cache != null) {
            cache.evict(userId);
        }
    }

    /** 限流降级：用户信息查询限流 */
    private UserVO getUserInfoRateLimitFallback(Long userId, Throwable t) {
        throw new BusinessException(ResultCode.TOO_MANY_REQUESTS);
    }

    /** 限流降级：修改密码限流 */
    private void changePasswordRateLimitFallback(ChangePasswordDTO dto, Throwable t) {
        throw new BusinessException(ResultCode.TOO_MANY_REQUESTS);
    }

    /** 限流降级：上传头像限流 */
    private String uploadAvatarRateLimitFallback(String base64, Throwable t) {
        throw new BusinessException(ResultCode.TOO_MANY_REQUESTS);
    }

    /** 限流降级：更换邮箱限流 */
    private void changeEmailRateLimitFallback(ChangeEmailDTO dto, Throwable t) {
        throw new BusinessException(ResultCode.TOO_MANY_REQUESTS);
    }

    /** 搜索降级：限流拒绝 → 抛 429；熔断打开/DB异常 → 返回空列表保前端可用 */
    private List<UserVO> searchFallback(String keyword, Throwable t) {
        if (t instanceof RequestNotPermitted) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS);
        }
        log.warn("[用户] 搜索熔断降级: keyword={}, err={}", keyword, t.getMessage());
        return Collections.emptyList();
    }
}
