package com.chatvibe.module.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chatvibe.common.exception.BusinessException;
import com.chatvibe.common.result.PageResult;
import com.chatvibe.common.result.ResultCode;
import com.chatvibe.module.admin.enums.OperationTypeEnum;
import com.chatvibe.module.admin.service.AdminLogService;
import com.chatvibe.module.admin.service.AdminUserService;
import com.chatvibe.module.admin.vo.SystemUserVO;
import com.chatvibe.module.auth.event.UserBanEvent;
import com.chatvibe.module.auth.event.UserBanEventProducer;
import com.chatvibe.module.auth.event.UserPasswordResetEvent;
import com.chatvibe.module.auth.event.UserPasswordResetEventProducer;
import com.chatvibe.module.user.entity.User;
import com.chatvibe.module.user.enums.UserRoleEnum;
import com.chatvibe.module.user.mapper.UserMapper;
import com.chatvibe.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.SecureRandom;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 管理员用户管理服务实现
 *
 * @author Alu
 * @date 2026-08-06
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int PASSWORD_LENGTH = 6;

    private final UserMapper userMapper;
    private final AdminLogService adminLogService;
    private final PasswordEncoder passwordEncoder;
    private final UserPasswordResetEventProducer passwordResetEventProducer;
    private final UserBanEventProducer userBanEventProducer;

    @Override
    public PageResult<SystemUserVO> getUserList(String keyword, String status, String role, int page, int size) {
        size = Math.min(size, 50);

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(User::getEmail, kw).or().like(User::getNickname, kw));
        }
        if ("banned".equals(status)) {
            wrapper.eq(User::getBanned, 1);
        } else if ("normal".equals(status)) {
            wrapper.eq(User::getBanned, 0);
        }
        if (role != null && !role.trim().isEmpty()) {
            wrapper.eq(User::getRole, role.trim());
        }
        wrapper.orderByDesc(User::getCreatedAt);

        Page<User> pageObj = new Page<>(page, size);
        Page<User> result = userMapper.selectPage(pageObj, wrapper);

        List<SystemUserVO> records = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        return PageResult.of(result.getTotal(), (long) page, (long) size, records);
    }

    /**
     * User -> SystemUserVO
     */
    private SystemUserVO toVO(User user) {
        SystemUserVO vo = new SystemUserVO();
        vo.setId(user.getId());
        vo.setEmail(user.getEmail());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setBio(user.getBio());
        vo.setRole(user.getRole());
        vo.setStatus(user.getBanned() != null && user.getBanned() == 1 ? "banned" : "normal");
        vo.setOnlineStatus(user.getStatus() != null ? user.getStatus() : 0);
        if (user.getCreatedAt() != null) {
            vo.setCreatedAt(user.getCreatedAt().format(DATE_TIME_FORMATTER));
        }
        if (user.getUpdatedAt() != null) {
            vo.setLastActiveAt(user.getUpdatedAt().format(DATE_TIME_FORMATTER));
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean banUser(Long userId, String type, String duration, String reason) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 权限分级校验
        String currentAdminRole = SecurityUtils.getCurrentUser().getRole();
        String targetRole = user.getRole();
        // 任何人都不能封禁超级管理员
        if (UserRoleEnum.SUPER_ADMIN.getCode().equals(targetRole)) {
            throw new BusinessException(ResultCode.CANNOT_BAN_ADMIN);
        }
        // 管理员只能封禁普通用户
        if (UserRoleEnum.ADMIN.getCode().equals(currentAdminRole)
                && !UserRoleEnum.USER.getCode().equals(targetRole)) {
            throw new BusinessException(ResultCode.NO_PERMISSION);
        }

        if (user.getBanned() != null && user.getBanned() == 1) {
            throw new BusinessException(ResultCode.USER_ALREADY_BANNED);
        }
        user.setBanned(1);
        user.setBanReason(reason);
        userMapper.updateById(user);
        adminLogService.log(OperationTypeEnum.USER_BAN, "封禁用户: " + user.getEmail() + ", 原因: " + reason);
        log.info("[管理员] 封禁用户成功: userId={}, email={}, type={}, duration={}", userId, user.getEmail(), type, duration);

        // 事务提交后异步发送封禁通知邮件（MQ 解耦，不阻塞响应）
        final String email = user.getEmail();
        final String nickname = user.getNickname();
        final long banTime = System.currentTimeMillis();
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        userBanEventProducer.sendBanEvent(
                                new UserBanEvent(userId, email, nickname, true, reason, type, duration, banTime));
                    }
                }
        );

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unbanUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 权限分级校验：管理员只能解封普通用户
        String currentAdminRole = SecurityUtils.getCurrentUser().getRole();
        String targetRole = user.getRole();
        if (UserRoleEnum.ADMIN.getCode().equals(currentAdminRole)
                && !UserRoleEnum.USER.getCode().equals(targetRole)) {
            throw new BusinessException(ResultCode.NO_PERMISSION);
        }

        if (user.getBanned() == null || user.getBanned() == 0) {
            throw new BusinessException(ResultCode.USER_NOT_BANNED);
        }
        user.setBanned(0);
        user.setBanReason(null);
        userMapper.updateById(user);
        adminLogService.log(OperationTypeEnum.USER_UNBAN, "解封用户: " + user.getEmail());
        log.info("[管理员] 解封用户成功: userId={}, email={}", userId, user.getEmail());

        // 事务提交后异步发送解封通知邮件（MQ 解耦，不阻塞响应）
        final String email = user.getEmail();
        final String nickname = user.getNickname();
        final long unbanTime = System.currentTimeMillis();
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        userBanEventProducer.sendBanEvent(
                                new UserBanEvent(userId, email, nickname, false, null, null, null, unbanTime));
                    }
                }
        );

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean changeUserRole(Long userId, String role, String reason) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (userId.equals(currentUserId)) {
            throw new BusinessException(ResultCode.CANNOT_CHANGE_OWN_ROLE);
        }
        // 校验角色值合法性
        if (!isValidRole(role)) {
            throw new BusinessException(ResultCode.PARAM_INVALID);
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 权限分级校验：管理员不能修改管理员/超级管理员角色，且只能设为运营或普通用户
        String currentAdminRole = SecurityUtils.getCurrentUser().getRole();
        if (UserRoleEnum.ADMIN.getCode().equals(currentAdminRole)) {
            if (UserRoleEnum.ADMIN.getCode().equals(user.getRole())
                    || UserRoleEnum.SUPER_ADMIN.getCode().equals(user.getRole())) {
                throw new BusinessException(ResultCode.NO_PERMISSION);
            }
            if (!UserRoleEnum.OPERATOR.getCode().equals(role)
                    && !UserRoleEnum.USER.getCode().equals(role)) {
                throw new BusinessException(ResultCode.NO_PERMISSION);
            }
        }

        user.setRole(role);
        userMapper.updateById(user);
        adminLogService.log(OperationTypeEnum.ROLE_CHANGE, "修改用户角色: " + user.getEmail() + ", 新角色: " + role + ", 原因: " + reason);
        log.info("[管理员] 修改用户角色成功: userId={}, email={}, role={}", userId, user.getEmail(), role);
        return true;
    }

    /**
     * 校验角色值是否合法
     */
    private boolean isValidRole(String role) {
        if (role == null || role.trim().isEmpty()) return false;
        return UserRoleEnum.USER.getCode().equals(role)
                || UserRoleEnum.OPERATOR.getCode().equals(role)
                || UserRoleEnum.ADMIN.getCode().equals(role)
                || UserRoleEnum.SUPER_ADMIN.getCode().equals(role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean resetUserPassword(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        String rawPassword = generateRandomPassword();
        user.setPassword(passwordEncoder.encode(rawPassword));
        userMapper.updateById(user);
        adminLogService.log(OperationTypeEnum.PASSWORD_RESET, "重置用户密码: " + user.getEmail());

        // 事务提交后异步发送新密码邮件（MQ 解耦，不阻塞响应）
        final String email = user.getEmail();
        final String newPassword = rawPassword;
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        passwordResetEventProducer.sendPasswordResetEvent(
                                new UserPasswordResetEvent(userId, email, System.currentTimeMillis(), newPassword));
                    }
                }
        );

        log.info("[管理员] 重置用户密码成功: userId={}, email={}", userId, user.getEmail());
        return true;
    }

    /**
     * 生成随机6位密码
     */
    private String generateRandomPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            sb.append(PASSWORD_CHARS.charAt(random.nextInt(PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }
}
