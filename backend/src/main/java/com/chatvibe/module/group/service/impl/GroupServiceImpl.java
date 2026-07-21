package com.chatvibe.module.group.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chatvibe.common.result.ResultCode;
import com.chatvibe.common.exception.BusinessException;
import com.chatvibe.module.chat.entity.Conversation;
import com.chatvibe.module.chat.entity.ConversationMember;
import com.chatvibe.module.chat.entity.Message;
import com.chatvibe.module.chat.enums.ConversationMemberRoleEnum;
import com.chatvibe.module.chat.enums.ConversationTypeEnum;
import com.chatvibe.module.chat.enums.MessageTypeEnum;
import com.chatvibe.module.chat.mapper.ConversationMapper;
import com.chatvibe.module.chat.mapper.ConversationMemberMapper;
import com.chatvibe.module.chat.mapper.MessageMapper;
import com.chatvibe.module.chat.service.ChatService;
import com.chatvibe.module.chat.vo.ConversationVO;
import com.chatvibe.module.group.dto.CreateGroupDTO;
import com.chatvibe.module.group.entity.GroupMember;
import com.chatvibe.module.group.event.*;
import com.chatvibe.module.group.mapper.GroupMemberMapper;
import com.chatvibe.module.group.service.GroupService;
import com.chatvibe.module.group.vo.GroupMemberVO;
import com.chatvibe.module.notification.enums.NotificationTypeEnum;
import com.chatvibe.module.notification.service.NotificationService;
import com.chatvibe.module.user.entity.User;
import com.chatvibe.module.user.service.UserService;
import com.chatvibe.security.SecurityUtils;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 群组服务实现
 *
 * @author Alu
 * @date 2026-06-28
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupServiceImpl implements GroupService {

    private final ChatService chatService;
    private final ConversationMapper conversationMapper;
    private final ConversationMemberMapper conversationMemberMapper;
    private final GroupMemberMapper groupMemberMapper;
    private final MessageMapper messageMapper;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationService notificationService;
    private final GroupInviteEventProducer groupInviteEventProducer;
    private final StringRedisTemplate stringRedisTemplate;
    private final CacheManager cacheManager;
    private final GroupRemoveEventProducer groupRemoveEventProducer;
    private final GroupDissolveEventProducer groupDissolveEventProducer;
    private final GroupTransferEventProducer groupTransferEventProducer;

    /** 创建群组 Redis 锁前缀，防双击重复建群 */
    private static final String GROUP_CREATE_LOCK_PREFIX = "group:create:lock:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(5);
    private static final String GROUP_DETAIL_CACHE = "groupDetail";
    private static final String GROUP_MEMBERS_CACHE = "groupMembers";
    private static final String GROUP_OPERATE_LOCK_PREFIX = "group:operate:lock:";
    /** 群组成员上限 */
    private static final int MAX_GROUP_MEMBERS = 200;

    /**
     * 系统消息：群组已被解散
     */
    private static final String DISSOLVE_SYSTEM_MESSAGE = "群组已被解散";

    @Override
    @RateLimiter(name = "createGroupRateLimiter", fallbackMethod = "createGroupFallback")
    @CircuitBreaker(name = "createGroupService", fallbackMethod = "createGroupFallback")
    @Transactional(rollbackFor = Exception.class)
    public ConversationVO createGroup(CreateGroupDTO dto) {
        Long ownerId = SecurityUtils.getCurrentUserId();
        // 1. memberIds 去重 + 排除群主自身 + 排除空值
        Set<Long> uniqueMemberIds = new LinkedHashSet<>();
        for (Long mid : dto.getMemberIds()) {
            if (mid != null && !mid.equals(ownerId)) {
                uniqueMemberIds.add(mid);
            }
        }
        if (uniqueMemberIds.size() + 1 > MAX_GROUP_MEMBERS) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "群组成员数量不能超过200人");
        }
        // 2. 批量校验被邀请用户是否存在（一次 IN 查询）
        if (!uniqueMemberIds.isEmpty()) {
            List<User> existUsers = userService.listByIds(new ArrayList<>(uniqueMemberIds));
            if (existUsers.size() != uniqueMemberIds.size()) {
                throw new BusinessException(ResultCode.USER_NOT_FOUND, "部分被邀请用户不存在");
            }
        }
        // 3. Redis 锁：防止用户双击重复建群
        String lockKey = GROUP_CREATE_LOCK_PREFIX + ownerId;
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            throw new BusinessException(ResultCode.FAIL, "请勿重复操作");
        }
        // 4. 创建群组会话 + 写入成员
        Conversation conversation = chatService.createGroupConversation(
                ownerId, dto.getName(), new ArrayList<>(uniqueMemberIds));
        // 设置群头像
        if (dto.getAvatar() != null) {
            conversationMapper.update(null, new LambdaUpdateWrapper<Conversation>()
                    .eq(Conversation::getId, conversation.getId())
                    .set(Conversation::getAvatar, dto.getAvatar()));
            conversation.setAvatar(dto.getAvatar());
        }
        // 同步写入 group_member 表
        syncGroupMembers(conversation.getId(), ownerId);
        log.info("[群组] 创建群组: groupId={}, name={}", conversation.getId(), dto.getName());
        // 5. 事务提交后异步发送群邀请通知事件
        if (!uniqueMemberIds.isEmpty()) {
            User operator = userService.getById(ownerId);
            String ownerNickname = operator != null ? operator.getNickname() : null;
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            groupInviteEventProducer.sendGroupInviteEvent(
                                    new GroupInviteEvent(
                                            conversation.getId(),
                                            ownerId,
                                            ownerNickname,
                                            dto.getName(),
                                            new ArrayList<>(uniqueMemberIds)
                                    )
                            );
                        }
                    }
            );
        }
        // 6. 直接从内存对象组装 VO，省去 getGroupDetail 的 2 次 DB 查询
        ConversationVO vo = new ConversationVO();
        BeanUtil.copyProperties(conversation, vo);
        return vo;
    }

    @Override
    @RateLimiter(name = "groupDetailRateLimiter", fallbackMethod = "getGroupDetailFallback")
    @CircuitBreaker(name = "groupDetailService", fallbackMethod = "getGroupDetailFallback")
    public ConversationVO getGroupDetail(Long groupId) {
        Long userId = SecurityUtils.getCurrentUserId();
        // 1. 先查群是否存在（优先走 Caffeine 缓存）
        Conversation conversation = getCachedConversation(groupId);
        if (conversation == null || conversation.getType() != ConversationTypeEnum.TYPE_GROUP.getCode()) {
            throw new BusinessException(ResultCode.GROUP_NOT_FOUND);
        }
        // 2. 再校验当前用户是否为群成员（权限检查，不缓存——用户维度太细）
        if (!chatService.isMember(groupId, userId)) {
            throw new BusinessException(ResultCode.NOT_CONVERSATION_MEMBER);
        }
        // 3. 从缓存的 Conversation 组装 VO（0 次额外 DB 查询）
        ConversationVO vo = new ConversationVO();
        BeanUtil.copyProperties(conversation, vo);
        return vo;
    }

    @Override
    @RateLimiter(name = "groupOperateRateLimiter", fallbackMethod = "groupOperateVoFallback")
    @CircuitBreaker(name = "groupOperateService", fallbackMethod = "groupOperateVoFallback")
    @Transactional(rollbackFor = Exception.class)
    public ConversationVO updateGroup(Long groupId, String name, String avatar) {
        if (StrUtil.isBlank(name) && StrUtil.isBlank(avatar)) {
            throw new BusinessException(ResultCode.PARAM_INVALID);
        }
        Long userId = SecurityUtils.getCurrentUserId();
        checkGroupOwnerOrAdmin(groupId, userId);
        // Redis 锁防双击
        String lockKey = GROUP_OPERATE_LOCK_PREFIX + "update:" + groupId;
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            throw new BusinessException(ResultCode.FAIL, "请勿重复操作");
        }
        LambdaUpdateWrapper<Conversation> wrapper = new LambdaUpdateWrapper<Conversation>()
                .eq(Conversation::getId, groupId);
        if (name != null) {
            wrapper.set(Conversation::getName, name);
        }
        if (avatar != null) {
            wrapper.set(Conversation::getAvatar, avatar);
        }
        conversationMapper.update(null, wrapper);
        evictGroupDetailCache(groupId);
        log.info("[群组] 编辑群信息: groupId={}", groupId);
        return getGroupDetail(groupId);
    }

    @Override
    @RateLimiter(name = "groupMemberListRateLimiter", fallbackMethod = "groupMemberListFallback")
    @CircuitBreaker(name = "groupMemberListService", fallbackMethod = "groupMemberListFallback")
    public List<GroupMemberVO> getGroupMembers(Long groupId) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (!chatService.isMember(groupId, userId)) {
            throw new BusinessException(ResultCode.NOT_CONVERSATION_MEMBER);
        }
        // 1. 查 Caffeine 缓存
        Cache cache = cacheManager.getCache(GROUP_MEMBERS_CACHE);
        if (cache != null) {
            Cache.ValueWrapper wrapper = cache.get(groupId);
            if (wrapper != null && wrapper.get() != null) {
                @SuppressWarnings("unchecked")
                List<GroupMemberVO> cached = (List<GroupMemberVO>) wrapper.get();
                if (cached != null) {
                    return cached;
                }
            }
        }
        // 2. 缓存未命中：一次查群成员 + 一次批量查用户(消除 N+1)
        List<GroupMember> groupMembers = groupMemberMapper.selectList(
                new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getConversationId, groupId)
                        .orderByDesc(GroupMember::getRole)
                        .orderByAsc(GroupMember::getJoinTime));
        if (groupMembers.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        Set<Long> userIds = groupMembers.stream().map(GroupMember::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
        // 3. 内存组装 VO
        List<GroupMemberVO> result = groupMembers.stream().map(gm -> {
            User user = userMap.get(gm.getUserId());
            if (user == null) return null;
            GroupMemberVO vo = new GroupMemberVO();
            vo.setId(user.getId());
            vo.setEmail(user.getEmail());
            vo.setNickname(user.getNickname());
            vo.setAvatar(user.getAvatar());
            vo.setBio(user.getBio());
            vo.setStatus(user.getStatus());
            vo.setRole(gm.getRole());
            vo.setJoinTime(gm.getJoinTime());
            return vo;
        }).filter(vo -> vo != null).collect(Collectors.toList());
        // 4. 回写缓存
        if (cache != null) {
            cache.put(groupId, result);
        }
        return result;
    }

    @Override
    @RateLimiter(name = "groupOperateRateLimiter", fallbackMethod = "groupOperateFallback")
    @CircuitBreaker(name = "groupOperateService", fallbackMethod = "groupOperateFallback")
    @Transactional(rollbackFor = Exception.class)
    public void inviteMembers(Long groupId, List<Long> memberIds) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (!chatService.isMember(groupId, userId)) {
            throw new BusinessException(ResultCode.NOT_CONVERSATION_MEMBER);
        }
        Conversation conversation = conversationMapper.selectById(groupId);
        if (conversation == null || conversation.getType() != ConversationTypeEnum.TYPE_GROUP.getCode()) {
            throw new BusinessException(ResultCode.GROUP_NOT_FOUND);
        }
        // Redis 锁防双击
        String lockKey = GROUP_OPERATE_LOCK_PREFIX + "invite:" + groupId;
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            throw new BusinessException(ResultCode.FAIL, "请勿重复操作");
        }
        // memberIds 去重
        Set<Long> uniqueIds = new LinkedHashSet<>(memberIds);
        List<String> invitedNames = new ArrayList<>();
        List<Long> invitedMemberIds = new ArrayList<>();
        for (Long memberId : uniqueIds) {
            ConversationMember existCm = conversationMemberMapper.selectIgnoreDeleted(groupId, memberId);
            if (existCm != null && Integer.valueOf(0).equals(existCm.getDeleted())) {
                continue;
            }
            invitedMemberIds.add(memberId);
            if (existCm != null) {
                conversationMemberMapper.restoreMember(groupId, memberId);
            } else {
                ConversationMember cm = new ConversationMember();
                cm.setConversationId(groupId);
                cm.setUserId(memberId);
                cm.setRole(ConversationMemberRoleEnum.ROLE_MEMBER.getCode());
                cm.setUnreadCount(0);
                cm.setMuted(0);
                cm.setLastReadAt(LocalDateTime.now());
                conversationMemberMapper.insert(cm);
            }
            GroupMember existGm = groupMemberMapper.selectIgnoreDeleted(groupId, memberId);
            if (existGm != null) {
                groupMemberMapper.restoreGroupMember(groupId, memberId);
            } else {
                GroupMember gm = new GroupMember();
                gm.setConversationId(groupId);
                gm.setUserId(memberId);
                gm.setRole(ConversationMemberRoleEnum.ROLE_MEMBER.getCode());
                gm.setJoinTime(LocalDateTime.now());
                groupMemberMapper.insert(gm);
            }
        }
        // 批量查昵称(消除 N+1)
        if (!invitedMemberIds.isEmpty()) {
            Map<Long, User> userMap = userService.listByIds(invitedMemberIds).stream()
                    .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
            for (Long mid : invitedMemberIds) {
                User u = userMap.get(mid);
                if (u != null && u.getNickname() != null) {
                    invitedNames.add(u.getNickname());
                }
            }
        }
        // 更新群成员数
        Long count = conversationMemberMapper.selectCount(
                new LambdaQueryWrapper<ConversationMember>()
                        .eq(ConversationMember::getConversationId, groupId));
        conversationMapper.update(null, new LambdaUpdateWrapper<Conversation>()
                .eq(Conversation::getId, groupId)
                .set(Conversation::getMemberCount, count == null ? 0 : count.intValue()));
        evictGroupDetailCache(groupId);
        evictGroupMembersCache(groupId);
        log.info("[群组] 邀请成员: groupId={}, invited={}", groupId, invitedMemberIds);
        // 系统消息落库(事务内)
        if (!invitedNames.isEmpty()) {
            User inviter = userService.getById(userId);
            String inviterName = inviter != null ? inviter.getNickname() : "未知用户";
            String joinNames = String.join("、", invitedNames);
            String sysContent = inviterName + " 邀请 " + joinNames + " 加入群聊";
            Message sysMsg = new Message();
            sysMsg.setConversationId(groupId);
            sysMsg.setSenderId(0L);
            sysMsg.setType(MessageTypeEnum.TYPE_SYSTEM.getCode());
            sysMsg.setContent(sysContent);
            sysMsg.setStatus(0);
            messageMapper.insert(sysMsg);
            conversationMapper.update(null, new LambdaUpdateWrapper<Conversation>()
                    .eq(Conversation::getId, groupId)
                    .set(Conversation::getLastMessage, sysContent)
                    .set(Conversation::getLastMessageAt, LocalDateTime.now()));
            evictGroupDetailCache(groupId);
            try {
                messagingTemplate.convertAndSend("/topic/conversation." + groupId, sysMsg);
            } catch (Exception e) {
                log.warn("[群组] 邀请系统消息推送失败: groupId={}, err={}", groupId, e.getMessage());
            }
        }
        // 事务提交后异步发送群邀请通知(复用 GroupInviteEvent)
        if (!invitedMemberIds.isEmpty()) {
            User operator = userService.getById(userId);
            String operatorName = operator != null ? operator.getNickname() : null;
            List<Long> finalInvitedIds = new ArrayList<>(invitedMemberIds);
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            groupInviteEventProducer.sendGroupInviteEvent(
                                    new GroupInviteEvent(groupId, userId, operatorName, conversation.getName(), finalInvitedIds)
                            );
                        }
                    }
            );
        }
    }

    @Override
    @RateLimiter(name = "groupOperateRateLimiter", fallbackMethod = "groupOperateFallback")
    @CircuitBreaker(name = "groupOperateService", fallbackMethod = "groupOperateFallback")
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(Long groupId, Long userId) {
        Long operatorId = SecurityUtils.getCurrentUserId();
        checkGroupOwner(groupId, operatorId);
        if (operatorId.equals(userId)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "群主不能移除自己，请使用退出群组");
        }
        // Redis 锁防双击
        String lockKey = GROUP_OPERATE_LOCK_PREFIX + "remove:" + groupId + ":" + userId;
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            throw new BusinessException(ResultCode.FAIL, "请勿重复操作");
        }
        conversationMemberMapper.delete(new LambdaQueryWrapper<ConversationMember>()
                .eq(ConversationMember::getConversationId, groupId)
                .eq(ConversationMember::getUserId, userId));
        groupMemberMapper.delete(new LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getConversationId, groupId)
                .eq(GroupMember::getUserId, userId));
        Long count = groupMemberMapper.selectCount(
                new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getConversationId, groupId)
                        .eq(GroupMember::getDeleted, 0));
        conversationMapper.update(null, new LambdaUpdateWrapper<Conversation>()
                .eq(Conversation::getId, groupId)
                .set(Conversation::getMemberCount, count));
        evictGroupDetailCache(groupId);
        evictGroupMembersCache(groupId);
        log.info("[群组] 移除成员: groupId={}, userId={}", groupId, userId);
        // 事务提交后异步发送移除通知
        Conversation removedConv = conversationMapper.selectById(groupId);
        String removedGroupName = removedConv != null ? removedConv.getName() : "";
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        groupRemoveEventProducer.sendGroupRemoveEvent(
                                new GroupRemoveEvent(groupId, userId, removedGroupName)
                        );
                    }
                }
        );
    }

    @Override
    @RateLimiter(name = "groupOperateRateLimiter", fallbackMethod = "groupOperateFallback")
    @CircuitBreaker(name = "groupOperateService", fallbackMethod = "groupOperateFallback")
    @Transactional(rollbackFor = Exception.class)
    public void leaveGroup(Long groupId) {
        Long userId = SecurityUtils.getCurrentUserId();
        Conversation conversation = conversationMapper.selectById(groupId);
        if (conversation == null || conversation.getType() != ConversationTypeEnum.TYPE_GROUP.getCode()) {
            throw new BusinessException(ResultCode.GROUP_NOT_FOUND);
        }
        if (conversation.getOwnerId() != null && conversation.getOwnerId().equals(userId)) {
            dissolveGroup(groupId);
            return;
        }
        // Redis 锁防双击
        String lockKey = GROUP_OPERATE_LOCK_PREFIX + "leave:" + groupId + ":" + userId;
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            throw new BusinessException(ResultCode.FAIL, "请勿重复操作");
        }
        conversationMemberMapper.delete(new LambdaQueryWrapper<ConversationMember>()
                .eq(ConversationMember::getConversationId, groupId)
                .eq(ConversationMember::getUserId, userId));
        groupMemberMapper.delete(new LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getConversationId, groupId)
                .eq(GroupMember::getUserId, userId));
        Long count = groupMemberMapper.selectCount(
                new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getConversationId, groupId)
                        .eq(GroupMember::getDeleted, 0));
        conversationMapper.update(null, new LambdaUpdateWrapper<Conversation>()
                .eq(Conversation::getId, groupId)
                .set(Conversation::getMemberCount, count));
        evictGroupDetailCache(groupId);
        evictGroupMembersCache(groupId);
        log.info("[群组] 退出群组: groupId={}, userId={}", groupId, userId);
    }

    @Override
    @RateLimiter(name = "groupOperateRateLimiter", fallbackMethod = "groupOperateFallback")
    @CircuitBreaker(name = "groupOperateService", fallbackMethod = "groupOperateFallback")
    @Transactional(rollbackFor = Exception.class)
    public void transferOwner(Long groupId, Long newOwnerId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Conversation conversation = conversationMapper.selectById(groupId);
        if (conversation == null || conversation.getType() != ConversationTypeEnum.TYPE_GROUP.getCode()) {
            throw new BusinessException(ResultCode.GROUP_NOT_FOUND);
        }
        if (conversation.getOwnerId() == null || !conversation.getOwnerId().equals(currentUserId)) {
            throw new BusinessException(ResultCode.NOT_GROUP_OWNER);
        }
        if (currentUserId.equals(newOwnerId)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "不能转让给自己");
        }
        GroupMember newOwner = groupMemberMapper.selectOne(new LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getConversationId, groupId)
                .eq(GroupMember::getUserId, newOwnerId));
        if (newOwner == null) {
            throw new BusinessException(ResultCode.NOT_CONVERSATION_MEMBER);
        }
        // Redis 锁防双击
        String lockKey = GROUP_OPERATE_LOCK_PREFIX + "transfer:" + groupId;
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            throw new BusinessException(ResultCode.FAIL, "请勿重复操作");
        }
        conversationMapper.update(null, new LambdaUpdateWrapper<Conversation>()
                .eq(Conversation::getId, groupId)
                .set(Conversation::getOwnerId, newOwnerId));
        evictGroupDetailCache(groupId);
        evictGroupMembersCache(groupId); // role 变了，成员列表也要清
        groupMemberMapper.update(null, new LambdaUpdateWrapper<GroupMember>()
                .eq(GroupMember::getConversationId, groupId)
                .eq(GroupMember::getUserId, currentUserId)
                .set(GroupMember::getRole, GroupMember.ROLE_MEMBER));
        groupMemberMapper.update(null, new LambdaUpdateWrapper<GroupMember>()
                .eq(GroupMember::getConversationId, groupId)
                .eq(GroupMember::getUserId, newOwnerId)
                .set(GroupMember::getRole, GroupMember.ROLE_OWNER));
        conversationMemberMapper.update(null, new LambdaUpdateWrapper<ConversationMember>()
                .eq(ConversationMember::getConversationId, groupId)
                .eq(ConversationMember::getUserId, currentUserId)
                .set(ConversationMember::getRole, ConversationMemberRoleEnum.ROLE_MEMBER.getCode()));
        conversationMemberMapper.update(null, new LambdaUpdateWrapper<ConversationMember>()
                .eq(ConversationMember::getConversationId, groupId)
                .eq(ConversationMember::getUserId, newOwnerId)
                .set(ConversationMember::getRole, ConversationMemberRoleEnum.ROLE_OWNER.getCode()));
        log.info("[群组] 转让群主: groupId={}, newOwnerId={}", groupId, newOwnerId);
        // 事务提交后异步通知新群主
        String groupName = conversation.getName();
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        groupTransferEventProducer.sendGroupTransferEvent(
                                new GroupTransferEvent(groupId, newOwnerId, groupName)
                        );
                    }
                }
        );
    }

    @Override
    @RateLimiter(name = "groupOperateRateLimiter", fallbackMethod = "groupOperateFallback")
    @CircuitBreaker(name = "groupOperateService", fallbackMethod = "groupOperateFallback")
    @Transactional(rollbackFor = Exception.class)
    public void dissolveGroup(Long groupId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Conversation conversation = conversationMapper.selectById(groupId);
        if (conversation == null || conversation.getType() != ConversationTypeEnum.TYPE_GROUP.getCode()) {
            throw new BusinessException(ResultCode.GROUP_NOT_FOUND);
        }
        if (conversation.getOwnerId() == null || !conversation.getOwnerId().equals(currentUserId)) {
            throw new BusinessException(ResultCode.NOT_GROUP_OWNER);
        }
        // Redis 锁防双击
        String lockKey = GROUP_OPERATE_LOCK_PREFIX + "dissolve:" + groupId;
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            throw new BusinessException(ResultCode.FAIL, "请勿重复操作");
        }
        // 1. 标记会话为已解散
        conversationMapper.update(null, new LambdaUpdateWrapper<Conversation>()
                .eq(Conversation::getId, groupId)
                .set(Conversation::getDissolved, 1)
                .set(Conversation::getLastMessage, DISSOLVE_SYSTEM_MESSAGE)
                .set(Conversation::getLastMessageAt, LocalDateTime.now()));
        evictGroupDetailCache(groupId);
        evictGroupMembersCache(groupId);
        // 2. 收集需要通知的成员(排除群主)
        String dissolveGroupName = conversation.getName();
        List<GroupMember> allMembers = groupMemberMapper.selectList(new LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getConversationId, groupId));
        List<Long> notifyMemberIds = allMembers.stream()
                .map(GroupMember::getUserId)
                .filter(uid -> !uid.equals(currentUserId))
                .collect(Collectors.toList());
        // 3. 删除群主的 member 记录
        conversationMemberMapper.delete(new LambdaQueryWrapper<ConversationMember>()
                .eq(ConversationMember::getConversationId, groupId)
                .eq(ConversationMember::getUserId, currentUserId));
        groupMemberMapper.delete(new LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getConversationId, groupId)
                .eq(GroupMember::getUserId, currentUserId));
        // 4. 落库系统消息
        Message sysMsg = new Message();
        sysMsg.setConversationId(groupId);
        sysMsg.setSenderId(0L);
        sysMsg.setType(MessageTypeEnum.TYPE_SYSTEM.getCode());
        sysMsg.setContent(DISSOLVE_SYSTEM_MESSAGE);
        sysMsg.setStatus(0);
        messageMapper.insert(sysMsg);
        // 5. WebSocket 广播(保持事务内,需即时性)
        try {
            messagingTemplate.convertAndSend("/topic/conversation." + groupId, sysMsg);
            messagingTemplate.convertAndSend("/topic/conversation." + groupId,
                    java.util.Map.of("event", "dissolved", "conversationId", groupId, "message", DISSOLVE_SYSTEM_MESSAGE));
        } catch (Exception e) {
            log.warn("[群组] 解散广播失败: groupId={}, err={}", groupId, e.getMessage());
        }
        log.info("[群组] 群主解散群组: groupId={}, ownerId={}", groupId, currentUserId);
        // 6. 事务提交后异步发送解散通知
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        groupDissolveEventProducer.sendGroupDissolveEvent(
                                new GroupDissolveEvent(groupId, dissolveGroupName, notifyMemberIds)
                        );
                    }
                }
        );
    }

    /**
     * 校验当前用户是否为群主
     */
    private void checkGroupOwner(Long groupId, Long userId) {
        GroupMember gm = groupMemberMapper.selectOne(new LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getConversationId, groupId)
                .eq(GroupMember::getUserId, userId));
        if (gm == null || gm.getRole() != GroupMember.ROLE_OWNER) {
            throw new BusinessException(ResultCode.NOT_GROUP_OWNER);
        }
    }

    /**
     * 校验当前用户是否为群主或管理员
     */
    private void checkGroupOwnerOrAdmin(Long groupId, Long userId) {
        GroupMember gm = groupMemberMapper.selectOne(new LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getConversationId, groupId)
                .eq(GroupMember::getUserId, userId));
        if (gm == null || (gm.getRole() != GroupMember.ROLE_OWNER && gm.getRole() != GroupMember.ROLE_ADMIN)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅群主或管理员可操作");
        }
    }

    /**
     * 同步写入 group_member 表(创建群组时)
     */
    private void syncGroupMembers(Long conversationId, Long ownerId) {
        List<ConversationMember> members = conversationMemberMapper.selectList(
                new LambdaQueryWrapper<ConversationMember>()
                        .eq(ConversationMember::getConversationId, conversationId));
        for (ConversationMember cm : members) {
            GroupMember gm = new GroupMember();
            gm.setConversationId(conversationId);
            gm.setUserId(cm.getUserId());
            gm.setRole(cm.getRole());
            gm.setJoinTime(LocalDateTime.now());
            groupMemberMapper.insert(gm);
        }
    }

    /**
     * 获取缓存的 Conversation（命中直接返回，未命中查库并回写）
     */
    private Conversation getCachedConversation(Long groupId) {
        Cache cache = cacheManager.getCache(GROUP_DETAIL_CACHE);
        if (cache != null) {
            Cache.ValueWrapper wrapper = cache.get(groupId);
            if (wrapper != null && wrapper.get() != null) {
                return (Conversation) wrapper.get();
            }
        }
        // 缓存未命中：查库
        Conversation conversation = conversationMapper.selectById(groupId);
        // 只缓存存在的会话（null 不缓存，避免缓存污染）
        if (conversation != null && cache != null) {
            cache.put(groupId, conversation);
        }
        return conversation;
    }

    /** 清除群详情缓存 */
    private void evictGroupDetailCache(Long groupId) {
        Cache cache = cacheManager.getCache(GROUP_DETAIL_CACHE);
        if (cache != null) {
            cache.evict(groupId);
        }
    }

    /** 建群降级：限流 → 429；业务异常 → 透传；其他 → 系统错误 */
    private ConversationVO createGroupFallback(CreateGroupDTO dto, Throwable t) {
        if (t instanceof RequestNotPermitted) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS);
        }
        if (t instanceof BusinessException) {
            throw (BusinessException) t;
        }
        log.warn("[熔断] 创建群组降级: err={}", t.getMessage());
        throw new BusinessException(ResultCode.SYSTEM_ERROR, "服务暂不可用，请稍后重试");
    }

    /** 群详情降级：限流 → 429；熔断 → 系统错误 */
    private ConversationVO getGroupDetailFallback(Long groupId, Throwable t) {
        if (t instanceof RequestNotPermitted) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS);
        }
        if (t instanceof BusinessException) {
            throw (BusinessException) t;
        }
        log.warn("[熔断] 群详情降级: groupId={}, err={}", groupId, t.getMessage());
        throw new BusinessException(ResultCode.SYSTEM_ERROR, "服务暂不可用，请稍后重试");
    }
    /** 群写操作降级：限流 → 429；业务异常 → 透传；其他 → 系统错误 */
    private void groupOperateFallback(Throwable t) {
        if (t instanceof RequestNotPermitted) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS);
        }
        if (t instanceof BusinessException) {
            throw (BusinessException) t;
        }
        log.warn("[熔断] 群操作降级: err={}", t.getMessage());
        throw new BusinessException(ResultCode.SYSTEM_ERROR, "服务暂不可用，请稍后重试");
    }

    /** 带 Long 参数的群写操作降级（用于单参数方法） */
    private void groupOperateFallback(Long id, Throwable t) {
        groupOperateFallback(t);
    }

    /** 带 Long+List 参数的群写操作降级（用于 inviteMembers） */
    private void groupOperateFallback(Long id, List<Long> ids, Throwable t) {
        groupOperateFallback(t);
    }

    /** 返回 ConversationVO 的群写操作降级（用于 updateGroup） */
    private ConversationVO groupOperateVoFallback(Throwable t) {
        groupOperateFallback(t);
        return null; // 不会执行到，groupOperateFallback 内部已抛异常
    }

    /** 群成员列表降级：限流 → 429；熔断 → 空列表 */
    private List<GroupMemberVO> groupMemberListFallback(Long groupId, Throwable t) {
        if (t instanceof RequestNotPermitted) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS);
        }
        if (t instanceof BusinessException) {
            throw (BusinessException) t;
        }
        log.warn("[熔断] 群成员列表降级: groupId={}, err={}", groupId, t.getMessage());
        return java.util.Collections.emptyList();
    }

    /** 清除群成员列表缓存 */
    private void evictGroupMembersCache(Long groupId) {
        Cache cache = cacheManager.getCache(GROUP_MEMBERS_CACHE);
        if (cache != null) {
            cache.evict(groupId);
        }
    }
}
