package com.chatvibe.module.admin.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chatvibe.common.exception.BusinessException;
import com.chatvibe.common.result.PageResult;
import com.chatvibe.common.result.ResultCode;
import com.chatvibe.module.admin.enums.OperationTypeEnum;
import com.chatvibe.module.admin.service.AdminGroupService;
import com.chatvibe.module.admin.service.AdminLogService;
import com.chatvibe.module.admin.vo.SystemGroupVO;
import com.chatvibe.module.chat.entity.Conversation;
import com.chatvibe.module.chat.mapper.ConversationMapper;
import com.chatvibe.module.user.entity.User;
import com.chatvibe.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理员群组管理服务实现
 *
 * @author Alu
 * @date 2026-08-07
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminGroupServiceImpl implements AdminGroupService {

    private final ConversationMapper conversationMapper;
    private final UserMapper userMapper;
    private final AdminLogService adminLogService;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public PageResult<SystemGroupVO> getGroupList(String keyword, Long ownerId, String status, int page, int size) {
        int safeSize = Math.min(size, 50);
        Page<Conversation> pageParam = new Page<>(page, safeSize);
        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<>();
        // 仅查询群聊(type=2)
        wrapper.eq(Conversation::getType, 2);
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.like(Conversation::getName, keyword);
        }
        if (ownerId != null) {
            wrapper.eq(Conversation::getOwnerId, ownerId);
        }
        if ("dissolved".equals(status)) {
            wrapper.eq(Conversation::getDissolved, 1);
        } else if ("normal".equals(status)) {
            wrapper.eq(Conversation::getDissolved, 0);
        }
        wrapper.orderByDesc(Conversation::getCreatedAt);
        Page<Conversation> result = conversationMapper.selectPage(pageParam, wrapper);

        List<Conversation> records = result.getRecords();
        if (records.isEmpty()) {
            return PageResult.of(result.getTotal(), (long) page, (long) safeSize, Collections.emptyList());
        }

        // 批量查询群主昵称(消除 N+1)
        Set<Long> ownerIds = records.stream()
                .map(Conversation::getOwnerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> ownerMap = ownerIds.isEmpty()
                ? Collections.emptyMap()
                : userMapper.selectList(new LambdaQueryWrapper<User>().in(User::getId, ownerIds))
                        .stream()
                        .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));

        List<SystemGroupVO> voList = records.stream().map(conv -> {
            SystemGroupVO vo = new SystemGroupVO();
            vo.setId(conv.getId());
            vo.setName(conv.getName());
            vo.setAvatar(conv.getAvatar());
            vo.setOwnerId(conv.getOwnerId());
            User owner = ownerMap.get(conv.getOwnerId());
            vo.setOwnerName(owner != null ? owner.getNickname() : null);
            vo.setMemberCount(conv.getMemberCount());
            vo.setStatus(conv.getDissolved() != null && conv.getDissolved() == 1 ? "dissolved" : "normal");
            vo.setCreatedAt(conv.getCreatedAt() != null ? conv.getCreatedAt().format(FORMATTER) : null);
            vo.setLastMessageAt(conv.getLastMessageAt() != null ? conv.getLastMessageAt().format(FORMATTER) : null);
            return vo;
        }).collect(Collectors.toList());

        return PageResult.of(result.getTotal(), (long) page, (long) safeSize, voList);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean dissolveGroup(Long groupId, String reason) {
        Conversation conv = conversationMapper.selectById(groupId);
        if (conv == null || conv.getType() != 2) {
            throw new BusinessException(ResultCode.GROUP_NOT_FOUND);
        }
        conv.setDissolved(1);
        conversationMapper.updateById(conv);
        adminLogService.log(OperationTypeEnum.GROUP_DISSOLVE,
                "解散群组: " + conv.getName() + ", 原因: " + reason);
        log.info("[管理员] 解散群组: groupId={}, name={}, reason={}", groupId, conv.getName(), reason);
        return true;
    }
}
