package com.chatvibe.module.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chatvibe.common.result.ResultCode;
import com.chatvibe.common.exception.BusinessException;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
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
    private static final String AVATAR_DIR = "uploads/avatars";

    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate stringRedisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final CacheManager cacheManager;

    @Value("${chatvibe.upload.url-prefix:/uploads}")
    private String avatarUrlPrefix;

    @Value("${chatvibe.upload.base-dir:./uploads}")
    private String avatarBaseDir;

    @Override
    public UserVO getCurrentUserInfo() {
        Long userId = SecurityUtils.getCurrentUserId();
        return getUserInfo(userId);
    }

    @Override
    @Cacheable(value = "userInfo", key = "#userId")
    public UserVO getUserInfo(Long userId) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return toVO(user);
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
        Cache cache = cacheManager.getCache("userInfo");
        if (cache != null){
            cache.evict(userId);
        }
        return toVO(user);
    }

    @Override
    public void changePassword(ChangePasswordDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.OLD_PASSWORD_ERROR);
        }
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        updateById(user);
        log.info("[用户] 修改密码成功, userId={}", userId);
    }

    @Override
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
        // 按月份分目录: user_avatar/2026-06/{uuid}.ext
        String monthDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String savedName = uuid + "." + ext;
        Path dirPath = Paths.get(avatarBaseDir, "user_avatar", monthDir);
        Path filePath = dirPath.resolve(savedName);
        try {
            Files.createDirectories(dirPath);
            Files.write(filePath, bytes);
        } catch (IOException e) {
            log.error("[用户] 头像写入磁盘失败: userId={}, path={}", userId, filePath, e);
            throw new BusinessException(ResultCode.FAIL, UserAvatarEnum.AVATAR_UPLOAD_FAILED.getMessage());
        }
        // 拼接可访问 URL: /uploads/user_avatar/2026-06/{uuid}.ext
        String prefix = avatarUrlPrefix.endsWith("/") ? avatarUrlPrefix : avatarUrlPrefix + "/";
        String url = prefix + "user_avatar/" + monthDir + "/" + savedName;
        update(new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getAvatar, url));
        log.info("[用户] 头像上传成功: userId={}, url={}", userId, url);
        Cache cache = cacheManager.getCache("userInfo");
        if (cache != null){
            cache.evict(userId);
        }
        return url;
    }

    @Override
    public List<UserVO> searchUsers(String keyword) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        List<User> users = baseMapper.searchUsers(keyword, currentUserId);
        return users.stream().map(this::toVO).collect(Collectors.toList());
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
        // 校验状态码有效性（统一在业务层处理）
        if (!UserStatusEnum.isValid(status)) {
            throw new BusinessException(ResultCode.PARAM_INVALID,
                    "状态值无效");
        }
        // 校验用户是否存在
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        // 无变化则跳过更新与广播，避免冗余推送
        if (status.equals(user.getStatus())) {
            return;
        }
        update(new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getStatus, status));
        broadcastStatus(userId, status);
    }

    @Override
    public void updateStatusManually(Long userId, Integer status) {
        updateStatus(userId, status);
    }

    private void broadcastStatus(Long userId, Integer status) {
        try {
            messagingTemplate.convertAndSend("/topic/status", new WsStatusMessage(userId, status));
        } catch (Exception e) {
            log.warn("[用户] 状态变更广播失败: userId={}, status={}, err={}", userId, status, e.getMessage());
        }
    }

    @Override
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
        // 邮箱是 JWT 身份凭证的一部分，更换后旧 Token 立即失效（JWT 过滤器会拒绝）。
        // 因此无需走登出接口，直接在此处置为离线并广播，避免状态残留和登出接口报错。
        updateStatus(userId, 0);
        log.info("[用户] 更换邮箱成功: userId={}, newEmail={}", userId, newEmail);
    }

    @Override
    public NotificationPreferencesVO getNotificationPreferences() {
        Long userId = SecurityUtils.getCurrentUserId();
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
    @Transactional(rollbackFor = Exception.class)
    public void updateNotificationPreferences(NotificationPreferencesVO vo) {
        Long userId = SecurityUtils.getCurrentUserId();
        LambdaUpdateWrapper<User> wrapper = new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId);
        if (vo.getDesktop() != null) {
            wrapper.set(User::getNotifyDesktop, vo.getDesktop() ? 1 : 0);
        }
        if (vo.getSound() != null) {
            wrapper.set(User::getNotifySound, vo.getSound() ? 1 : 0);
        }
        if (vo.getAiAlert() != null) {
            wrapper.set(User::getNotifyAiAlert, vo.getAiAlert() ? 1 : 0);
        }
        update(wrapper);
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
}
