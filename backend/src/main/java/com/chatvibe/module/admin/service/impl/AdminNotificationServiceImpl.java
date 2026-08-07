package com.chatvibe.module.admin.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chatvibe.common.result.PageResult;
import com.chatvibe.module.admin.service.AdminNotificationService;
import com.chatvibe.module.admin.vo.NotificationRecordVO;
import com.chatvibe.module.notification.entity.Notification;
import com.chatvibe.module.notification.enums.NotificationTypeEnum;
import com.chatvibe.module.notification.mapper.NotificationMapper;
import com.chatvibe.module.user.entity.User;
import com.chatvibe.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理员通知发送记录服务实现
 * 只读查询系统自动通知（非公告）的发送记录
 *
 * @author Alu
 * @date 2026-08-07
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminNotificationServiceImpl implements AdminNotificationService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationMapper notificationMapper;
    private final UserMapper userMapper;

    @Override
    public PageResult<NotificationRecordVO> getNotificationList(Integer type, String startDate, String endDate, String keyword, Integer isRead, int page, int size) {
        int pageSize = Math.min(size, MAX_PAGE_SIZE);
        Page<Notification> pageParam = new Page<>(page, pageSize);

        // 根据关键词模糊匹配用户昵称/邮箱，获取匹配的用户ID列表
        List<Long> matchedUserIds = null;
        if (StrUtil.isNotBlank(keyword)) {
            List<User> matchedUsers = userMapper.selectList(new LambdaQueryWrapper<User>()
                    .select(User::getId)
                    .and(w -> w.like(User::getNickname, keyword).or().like(User::getEmail, keyword)));
            matchedUserIds = matchedUsers.stream().map(User::getId).collect(Collectors.toList());
            if (matchedUserIds.isEmpty()) {
                return PageResult.of(0L, (long) page, (long) pageSize, Collections.emptyList());
            }
        }

        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                .eq(type != null, Notification::getType, type)
                .in(matchedUserIds != null, Notification::getUserId, matchedUserIds)
                .eq(isRead != null, Notification::getIsRead, isRead)
                .ge(StrUtil.isNotBlank(startDate), Notification::getCreatedAt, parseStartOfDay(startDate))
                .le(StrUtil.isNotBlank(endDate), Notification::getCreatedAt, parseEndOfDay(endDate))
                .orderByDesc(Notification::getCreatedAt);

        Page<Notification> result = notificationMapper.selectPage(pageParam, wrapper);

        if (result.getRecords().isEmpty()) {
            return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), Collections.emptyList());
        }

        // 批量查询用户信息，避免 N+1
        Set<Long> userIds = result.getRecords().stream()
                .map(Notification::getUserId)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = batchQueryUsers(userIds);

        List<NotificationRecordVO> records = result.getRecords().stream()
                .map(n -> toVO(n, userMap))
                .collect(Collectors.toList());

        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), records);
    }

    /**
     * 批量查询用户信息
     */
    private Map<Long, User> batchQueryUsers(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<User> users = userMapper.selectByIdsIn(userIds);
        Map<Long, User> map = new HashMap<>(users.size());
        for (User user : users) {
            map.put(user.getId(), user);
        }
        return map;
    }

    /**
     * 解析开始日期为当天 00:00:00
     */
    private LocalDateTime parseStartOfDay(String dateStr) {
        try {
            return LocalDate.parse(dateStr).atStartOfDay();
        } catch (Exception e) {
            log.warn("[通知记录] 解析开始日期失败: {}", dateStr);
            return null;
        }
    }

    /**
     * 解析结束日期为当天 23:59:59
     */
    private LocalDateTime parseEndOfDay(String dateStr) {
        try {
            return LocalDate.parse(dateStr).atTime(23, 59, 59);
        } catch (Exception e) {
            log.warn("[通知记录] 解析结束日期失败: {}", dateStr);
            return null;
        }
    }

    /**
     * Notification 转 NotificationRecordVO
     */
    private NotificationRecordVO toVO(Notification notification, Map<Long, User> userMap) {
        NotificationRecordVO vo = new NotificationRecordVO();
        vo.setId(notification.getId());
        vo.setUserId(notification.getUserId());
        vo.setType(notification.getType());
        NotificationTypeEnum typeEnum = NotificationTypeEnum.fromCode(notification.getType());
        vo.setTypeDesc(typeEnum != null ? typeEnum.getDescription() : null);
        vo.setTitle(notification.getTitle());
        vo.setContent(notification.getContent());
        vo.setExtra(notification.getExtra());
        vo.setIsRead(notification.getIsRead());

        User user = userMap.get(notification.getUserId());
        if (user != null) {
            vo.setUserNickname(user.getNickname());
            vo.setUserEmail(user.getEmail());
        }

        LocalDateTime createdAt = notification.getCreatedAt();
        if (createdAt != null) {
            vo.setCreatedAt(createdAt.format(DATE_TIME_FORMATTER));
        }
        return vo;
    }
}
