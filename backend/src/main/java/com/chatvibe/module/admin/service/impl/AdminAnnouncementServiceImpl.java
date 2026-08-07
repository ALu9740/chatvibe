package com.chatvibe.module.admin.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chatvibe.common.exception.BusinessException;
import com.chatvibe.common.result.PageResult;
import com.chatvibe.common.result.ResultCode;
import com.chatvibe.module.admin.dto.CreateAnnouncementDTO;
import com.chatvibe.module.admin.entity.Announcement;
import com.chatvibe.module.admin.enums.OperationTypeEnum;
import com.chatvibe.module.admin.event.AnnouncementEventProducer;
import com.chatvibe.module.admin.event.AnnouncementPublishEvent;
import com.chatvibe.module.admin.mapper.AnnouncementMapper;
import com.chatvibe.module.admin.service.AdminAnnouncementService;
import com.chatvibe.module.admin.service.AdminLogService;
import com.chatvibe.module.admin.vo.AnnouncementVO;
import com.chatvibe.module.notification.entity.Notification;
import com.chatvibe.module.notification.mapper.NotificationMapper;
import com.chatvibe.module.user.entity.User;
import com.chatvibe.module.user.mapper.UserMapper;
import com.chatvibe.security.LoginUser;
import com.chatvibe.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理员公告服务实现
 *
 * 功能：
 * 1. 公告创建与发布（MQ 异步创建通知，避免大量用户阻塞）
 * 2. 公告历史与撤回（撤回时批量删除关联通知记录）
 * 3. 权限控制（SUPER_ADMIN/ADMIN 可撤回任意公告，OPERATOR 仅可撤回自己发布的）
 *
 * @author Alu
 * @date 2026-08-07
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAnnouncementServiceImpl implements AdminAnnouncementService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String SCOPE_ALL = "all";
    private static final String SCOPE_SPECIFIED = "specified";
    private static final String STATUS_PUBLISHED = "published";
    private static final String STATUS_WITHDRAWN = "withdrawn";
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_SPECIFIED_USERS = 1000;

    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    private static final String ROLE_ADMIN = "ADMIN";

    private final AnnouncementMapper announcementMapper;
    private final NotificationMapper notificationMapper;
    private final UserMapper userMapper;
    private final AdminLogService adminLogService;
    private final AnnouncementEventProducer announcementEventProducer;
    private final CacheManager cacheManager;

    @Override
    public PageResult<AnnouncementVO> getAnnouncementList(String keyword, int page, int size) {
        int pageSize = Math.min(size, MAX_PAGE_SIZE);
        Page<Announcement> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<Announcement> wrapper = new LambdaQueryWrapper<Announcement>()
                .like(StrUtil.isNotBlank(keyword), Announcement::getTitle, keyword)
                .orderByDesc(Announcement::getCreatedAt);
        Page<Announcement> result = announcementMapper.selectPage(pageParam, wrapper);

        List<AnnouncementVO> records = new ArrayList<>();
        for (Announcement announcement : result.getRecords()) {
            records.add(toVO(announcement));
        }
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), records);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean createAnnouncement(CreateAnnouncementDTO dto) {
        LoginUser currentUser = SecurityUtils.getCurrentUser();
        String currentEmail = currentUser.getEmail();

        // 确定目标用户列表
        List<Long> targetUserIds;
        String scope;
        if (SCOPE_SPECIFIED.equals(dto.getScope())) {
            if (dto.getTargetUserIds() == null || dto.getTargetUserIds().isEmpty()) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "请选择至少一名目标用户");
            }
            if (dto.getTargetUserIds().size() > MAX_SPECIFIED_USERS) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "指定用户最多选择" + MAX_SPECIFIED_USERS + "人");
            }
            scope = SCOPE_SPECIFIED;
            targetUserIds = dto.getTargetUserIds();
        } else {
            scope = SCOPE_ALL;
            targetUserIds = getAllUserIds();
        }

        // 创建公告记录
        Announcement announcement = new Announcement();
        announcement.setTitle(dto.getTitle());
        announcement.setContent(dto.getContent());
        announcement.setScope(scope);
        announcement.setTargetCount(targetUserIds.size());
        announcement.setStatus(STATUS_PUBLISHED);
        announcement.setCreatedBy(currentEmail);

        announcementMapper.insert(announcement);
        log.info("[公告] 发布公告成功: id={}, title={}, scope={}, targetCount={}, createdBy={}",
                announcement.getId(), announcement.getTitle(), announcement.getScope(),
                announcement.getTargetCount(), currentEmail);

        // 事务提交后通过 MQ 异步创建通知（避免大量用户时阻塞请求）
        final Long announcementId = announcement.getId();
        final List<Long> finalTargetUserIds = targetUserIds;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                AnnouncementPublishEvent event = new AnnouncementPublishEvent(
                        announcementId,
                        announcement.getTitle(),
                        announcement.getContent(),
                        finalTargetUserIds
                );
                announcementEventProducer.sendAnnouncementPublishEvent(event);
            }
        });

        adminLogService.log(OperationTypeEnum.ANNOUNCEMENT_PUBLISH, "发布公告: " + dto.getTitle());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean withdrawAnnouncement(Long id) {
        LoginUser currentUser = SecurityUtils.getCurrentUser();
        String currentEmail = currentUser.getEmail();
        String currentRole = currentUser.getRole();

        Announcement announcement = announcementMapper.selectById(id);
        if (announcement == null) {
            throw new BusinessException(ResultCode.ANNOUNCEMENT_NOT_FOUND);
        }
        if (STATUS_WITHDRAWN.equals(announcement.getStatus())) {
            throw new BusinessException(ResultCode.ANNOUNCEMENT_ALREADY_WITHDRAWN);
        }

        // 权限检查：OPERATOR 仅可撤回自己发布的公告，SUPER_ADMIN 和 ADMIN 可撤回任意公告
        boolean isPrivileged = ROLE_SUPER_ADMIN.equals(currentRole) || ROLE_ADMIN.equals(currentRole);
        if (!isPrivileged && !currentEmail.equals(announcement.getCreatedBy())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权撤回他人发布的公告");
        }

        // 更新公告状态为已撤回
        announcement.setStatus(STATUS_WITHDRAWN);
        announcementMapper.updateById(announcement);
        log.info("[公告] 撤回公告成功: id={}, title={}, operator={}", id, announcement.getTitle(), currentEmail);

        // 查询受影响的用户ID列表（用于清除未读数缓存）
        List<Notification> affectedNotifs = notificationMapper.selectList(new LambdaQueryWrapper<Notification>()
                .select(Notification::getUserId, Notification::getIsRead)
                .eq(Notification::getAnnouncementId, id));

        // 批量删除关联通知记录（@TableLogic 自动转为 SET deleted=1 WHERE deleted=0）
        int deletedCount = notificationMapper.delete(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getAnnouncementId, id));
        log.info("[公告] 撤回公告删除关联通知: announcementId={}, deletedCount={}", id, deletedCount);

        // 清除受影响用户的未读数缓存（仅清除有未读通知的用户）
        Set<Long> affectedUserIds = affectedNotifs.stream()
                .filter(n -> n.getIsRead() != null && n.getIsRead() == 0)
                .map(Notification::getUserId)
                .collect(Collectors.toSet());
        Cache cache = cacheManager.getCache("notifUnreadCount");
        if (cache != null) {
            for (Long uid : affectedUserIds) {
                cache.evict(uid);
            }
        }

        adminLogService.log(OperationTypeEnum.ANNOUNCEMENT_WITHDRAW, "撤回公告: " + announcement.getTitle());
        return true;
    }

    /**
     * 获取所有未删除用户的ID列表
     *
     * @return 用户ID列表
     */
    private List<Long> getAllUserIds() {
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .select(User::getId));
        List<Long> userIds = new ArrayList<>(users.size());
        for (User user : users) {
            userIds.add(user.getId());
        }
        return userIds;
    }

    /**
     * Announcement 转 AnnouncementVO
     *
     * @param announcement 公告实体
     * @return 公告VO
     */
    private AnnouncementVO toVO(Announcement announcement) {
        AnnouncementVO vo = new AnnouncementVO();
        vo.setId(announcement.getId());
        vo.setTitle(announcement.getTitle());
        vo.setContent(announcement.getContent());
        vo.setScope(announcement.getScope());
        vo.setTargetCount(announcement.getTargetCount());
        vo.setStatus(announcement.getStatus());
        vo.setCreatedBy(announcement.getCreatedBy());
        LocalDateTime createdAt = announcement.getCreatedAt();
        if (createdAt != null) {
            vo.setCreatedAt(createdAt.format(DATE_TIME_FORMATTER));
        }
        return vo;
    }
}
