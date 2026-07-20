package com.chatvibe.module.friend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chatvibe.common.result.ResultCode;
import com.chatvibe.common.exception.BusinessException;
import com.chatvibe.module.chat.entity.Conversation;
import com.chatvibe.module.chat.mapper.ConversationMapper;
import com.chatvibe.module.chat.mapper.ConversationMemberMapper;
import com.chatvibe.module.chat.service.ChatService;
import com.chatvibe.module.friend.entity.FriendRequest;
import com.chatvibe.module.friend.enums.FriedStatusEnum;
import com.chatvibe.module.friend.event.FriendAcceptEvent;
import com.chatvibe.module.friend.event.FriendAcceptEventProducer;
import com.chatvibe.module.friend.event.FriendDeleteEvent;
import com.chatvibe.module.friend.event.FriendDeleteEventProducer;
import com.chatvibe.module.friend.event.FriendRequestEvent;
import com.chatvibe.module.friend.event.FriendRequestEventProducer;
import com.chatvibe.module.friend.mapper.FriendRequestMapper;
import com.chatvibe.module.friend.service.FriendService;
import com.chatvibe.module.friend.vo.FriendRequestVO;
import com.chatvibe.module.user.entity.User;
import com.chatvibe.module.user.service.UserService;
import com.chatvibe.module.user.vo.UserVO;
import com.chatvibe.security.SecurityUtils;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.time.Duration;

/**
 * 好友服务实现
 *
 * @author Alu
 * @date 2026-06-28
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FriendServiceImpl implements FriendService {

    private final FriendRequestMapper friendRequestMapper;
    private final UserService userService;
    private final ChatService chatService;
    private final ConversationMapper conversationMapper;
    private final ConversationMemberMapper conversationMemberMapper;
    private final CacheManager cacheManager;
    private final FriendRequestEventProducer friendRequestEventProducer;
    private final StringRedisTemplate stringRedisTemplate;
    private final FriendAcceptEventProducer friendAcceptEventProducer;
    private final FriendDeleteEventProducer friendDeleteEventProducer;

    private static final String FRIEND_LIST_CACHE = "friendList";
    private static final String FRIEND_REQUEST_LOCK_PREFIX = "friend:request:lock:";
    private static final String FRIEND_REQUEST_ACCEPT_LOCK_PREFIX = "friend:request:accept:lock:";
    private static final String FRIEND_DELETE_LOCK_PREFIX = "friend:delete:lock:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(5);

    @Override
    public List<UserVO> searchUsers(String keyword) {
        return userService.searchUsers(keyword);
    }

    @Override
    @RateLimiter(name = "friendRequestRateLimiter", fallbackMethod = "sendFriendRequestFallback")
    @CircuitBreaker(name = "friendRequestService", fallbackMethod = "sendFriendRequestFallback")
    @Transactional(rollbackFor = Exception.class)
    public void sendFriendRequest(Long toUid, String message) {
        Long fromUid = SecurityUtils.getCurrentUserId();
        if (fromUid.equals(toUid)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "不能添加自己为好友");
        }
        // message 长度校验
        if (message != null && message.length() > 255) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "验证消息过长");
        }
        // Redis 分布式锁：防止 (fromUid,toUid) 并发重复请求
        String lockKey = FRIEND_REQUEST_LOCK_PREFIX + fromUid + ":" + toUid;
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            throw new BusinessException(ResultCode.FRIEND_REQUEST_EXISTS);
        }

        // 校验目标用户是否存在
        User toUser = userService.getById(toUid);
        if (toUser == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        // 校验是否已是好友(已有私聊会话)
        Conversation exist = conversationMapper.selectPrivateConversation(fromUid, toUid);
        if (exist != null) {
            throw new BusinessException(ResultCode.USER_ALREADY_FRIEND);
        }
        // 校验是否已发送过待处理请求
        Long existCount = friendRequestMapper.selectCount(new LambdaQueryWrapper<FriendRequest>()
                .eq(FriendRequest::getFromUid, fromUid)
                .eq(FriendRequest::getToUid, toUid)
                .eq(FriendRequest::getStatus, FriedStatusEnum.STATUS_PENDING));
        if (existCount > 0) {
            throw new BusinessException(ResultCode.FRIEND_REQUEST_EXISTS);
        }
        // 校验对方是否已向自己发送待处理请求（反向检查）
        Long reverseCount = friendRequestMapper.selectCount(new LambdaQueryWrapper<FriendRequest>()
                .eq(FriendRequest::getFromUid, toUid)
                .eq(FriendRequest::getToUid, fromUid)
                .eq(FriendRequest::getStatus, FriedStatusEnum.STATUS_PENDING));
        if (reverseCount > 0) {
            throw new BusinessException(ResultCode.FRIEND_REQUEST_RECEIVED);
        }
        // 创建好友请求
        FriendRequest request = new FriendRequest();
        request.setFromUid(fromUid);
        request.setToUid(toUid);
        request.setMessage(message);
        request.setStatus(FriedStatusEnum.STATUS_PENDING.getCode());
        friendRequestMapper.insert(request);
        log.info("[好友] 发送好友请求: from={}, to={}", fromUid, toUid);

        // 事务提交后异步发送通知事件
        User fromUser = userService.getById(fromUid);
        String fromNickname = fromUser != null ? fromUser.getNickname() : null;
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        friendRequestEventProducer.sendFriendRequestEvent(
                                new FriendRequestEvent(request.getId(), fromUid, toUid, fromNickname, message)
                        );
                    }
                }
        );
    }

    @Override
    @RateLimiter(name = "friendRequestListRateLimiter", fallbackMethod = "listFallback")
    @CircuitBreaker(name = "friendListService", fallbackMethod = "listFallback")
    public List<FriendRequestVO> getReceivedRequests() {
        Long userId = SecurityUtils.getCurrentUserId();
        List<FriendRequest> requests = friendRequestMapper.selectList(
                new LambdaQueryWrapper<FriendRequest>()
                        .eq(FriendRequest::getToUid, userId)
                        .eq(FriendRequest::getStatus, FriedStatusEnum.STATUS_PENDING.getCode())
                        .orderByDesc(FriendRequest::getCreatedAt));
        return batchToVO(requests);
    }

    @Override
    @RateLimiter(name = "friendRequestListRateLimiter", fallbackMethod = "listFallback")
    @CircuitBreaker(name = "friendListService", fallbackMethod = "listFallback")
    public List<FriendRequestVO> getSentRequests() {
        Long userId = SecurityUtils.getCurrentUserId();
        List<FriendRequest> requests = friendRequestMapper.selectList(
                new LambdaQueryWrapper<FriendRequest>()
                        .eq(FriendRequest::getFromUid, userId)
                        .eq(FriendRequest::getStatus, FriedStatusEnum.STATUS_PENDING.getCode())
                        .orderByDesc(FriendRequest::getCreatedAt));
        return batchToVO(requests);
    }

    @Override
    @RateLimiter(name = "acceptRequestRateLimiter", fallbackMethod = "acceptRequestFallback")
    @CircuitBreaker(name = "acceptRequestService", fallbackMethod = "acceptRequestFallback")
    @Transactional(rollbackFor = Exception.class)
    public void acceptRequest(Long requestId) {
        Long userId = SecurityUtils.getCurrentUserId();
        // Redis 锁：防止用户对同一请求并发双击
        String lockKey = FRIEND_REQUEST_ACCEPT_LOCK_PREFIX + requestId;
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            throw new BusinessException(ResultCode.FAIL, "请勿重复操作");
        }
        FriendRequest request = friendRequestMapper.selectById(requestId);
        if (request == null) {
            throw new BusinessException(ResultCode.FRIEND_REQUEST_NOT_FOUND);
        }
        if (!request.getToUid().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权处理此好友请求");
        }
        if (!request.getStatus().equals(FriedStatusEnum.STATUS_PENDING.getCode())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "该请求已处理");
        }
        // 更新请求状态（乐观锁兜底：status=PENDING 才更新，防止锁失效的极端并发）
        int updated = friendRequestMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<FriendRequest>()
                        .eq(FriendRequest::getId, requestId)
                        .eq(FriendRequest::getStatus, FriedStatusEnum.STATUS_PENDING.getCode())
                        .set(FriendRequest::getStatus, FriedStatusEnum.STATUS_ACCEPTED.getCode()));
        if (updated == 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "该请求已处理");
        }
        // 创建私聊会话
        chatService.createPrivateConversation(request.getFromUid(), request.getToUid());
        log.info("[好友] 接受好友请求: requestId={}, from={}, to={}",
                requestId, request.getFromUid(), request.getToUid());

        // 事务提交后异步发送通知事件
        User acceptUser = userService.getById(userId);
        String acceptNickname = acceptUser != null ? acceptUser.getNickname() : null;
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        friendAcceptEventProducer.sendFriendAcceptEvent(
                                new FriendAcceptEvent(requestId, request.getFromUid(), userId, acceptNickname)
                        );
                        // 清除好友列表缓存（双方）
                        evictFriendListCache(request.getFromUid());
                        evictFriendListCache(userId);
                    }
                }
        );
    }

    @Override
    @RateLimiter(name = "acceptRequestRateLimiter", fallbackMethod = "acceptRequestFallback")
    @CircuitBreaker(name = "rejectRequestService", fallbackMethod = "acceptRequestFallback")
    @Transactional(rollbackFor = Exception.class)
    public void rejectRequest(Long requestId) {
        Long userId = SecurityUtils.getCurrentUserId();
        String lockKey = FRIEND_REQUEST_ACCEPT_LOCK_PREFIX + requestId;
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            throw new BusinessException(ResultCode.FAIL, "请勿重复操作");
        }
        FriendRequest request = friendRequestMapper.selectById(requestId);
        if (request == null) {
            throw new BusinessException(ResultCode.FRIEND_REQUEST_NOT_FOUND);
        }
        if (!request.getToUid().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权处理此好友请求");
        }
        if (!Integer.valueOf(FriedStatusEnum.STATUS_PENDING.getCode()).equals(request.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "该请求已处理");
        }
        // 乐观锁更新（status=PENDING 才更新）
        int updated = friendRequestMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<FriendRequest>()
                        .eq(FriendRequest::getId, requestId)
                        .eq(FriendRequest::getStatus, FriedStatusEnum.STATUS_PENDING.getCode())
                        .set(FriendRequest::getStatus, FriedStatusEnum.STATUS_REJECTED.getCode()));
        if (updated == 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "该请求已处理");
        }
        log.info("[好友] 拒绝好友请求: requestId={}", requestId);
    }

    @Override
    @RateLimiter(name = "friendListRateLimiter", fallbackMethod = "listFallback")
    @CircuitBreaker(name = "friendListService", fallbackMethod = "listFallback")
    public List<UserVO> getFriendList() {
        Long userId = SecurityUtils.getCurrentUserId();
        // 先查 Caffeine 缓存
        Cache cache = cacheManager.getCache(FRIEND_LIST_CACHE);
        if (cache != null) {
            Cache.ValueWrapper wrapper = cache.get(userId);
            if (wrapper != null) {
                @SuppressWarnings("unchecked")
                List<UserVO> cached = (List<UserVO>) wrapper.get();
                if (cached != null) {
                    return cached;
                }
            }
        }
        // 未命中：一次查询好友 ID，一次批量查询用户信息（消除 N+1）
        List<Long> friendIds = conversationMemberMapper.selectFriendIdsIgnoreDeleted(userId);
        List<UserVO> friends = friendIds.isEmpty()
                ? Collections.emptyList()
                : userService.listByIdsIn(friendIds);
        // 回写缓存
        if (cache != null) {
            cache.put(userId, friends);
        }
        return friends;
    }

    @Override
    @RateLimiter(name = "deleteFriendRateLimiter", fallbackMethod = "acceptRequestFallback")
    @CircuitBreaker(name = "deleteFriendService", fallbackMethod = "acceptRequestFallback")
    @Transactional(rollbackFor = Exception.class)
    public void deleteFriend(Long friendId) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId.equals(friendId)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "不能删除自己");
        }
        // Redis 锁：防止对同一好友并发删除，或与 sendFriendRequest 竞态
        String lockKey = FRIEND_DELETE_LOCK_PREFIX + userId + ":" + friendId;
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            throw new BusinessException(ResultCode.FAIL, "请勿重复操作");
        }
        // 查找当前用户与好友之间的私聊会话
        Conversation conversation = conversationMapper.selectPrivateConversation(userId, friendId);
        if (conversation == null) {
            throw new BusinessException(ResultCode.CONVERSATION_NOT_FOUND);
        }
        // 物理删除双向 conversation_member 记录（彻底解除好友关系）。
        // 区别于删除会话的逻辑删除（隐藏会话，清除聊天记录，保留好友关系）：物理删除使记录彻底不存在，
        // selectFriendIdsIgnoreDeleted（绕过 @TableLogic 的原生 SQL）将查不到该好友，
        // 从而保证删除好友后立即从"我的好友"列表消失，无法再次创建会话。
        conversationMemberMapper.physicalDeleteMembers(conversation.getId(), userId, friendId);
        // 逻辑删除私聊会话(deleted=1)
        conversationMapper.deleteById(conversation.getId());
        log.info("[好友] 删除好友: userId={}, friendId={}, conversationId={}",
                userId, friendId, conversation.getId());

        // 事务提交后异步发送通知事件 + 清缓存
        User currentUser = userService.getById(userId);
        String currentNickname = currentUser != null ? currentUser.getNickname() : null;
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        friendDeleteEventProducer.sendFriendDeleteEvent(
                                new FriendDeleteEvent(userId, friendId, currentNickname)
                        );
                        // 清除双方好友列表缓存
                        evictFriendListCache(userId);
                        evictFriendListCache(friendId);
                    }
                }
        );
    }

    /**
     * 批量转换 VO：一次 IN 查询所有相关用户，消除 N+1
     */
    private List<FriendRequestVO> batchToVO(List<FriendRequest> requests) {
        if (requests.isEmpty()) {
            return Collections.emptyList();
        }
        // 收集所有 fromUid + toUid
        Set<Long> userIds = new HashSet<>();
        for (FriendRequest r : requests) {
            userIds.add(r.getFromUid());
            userIds.add(r.getToUid());
        }
        // 一次查询所有用户
        Map<Long, UserVO> userMap = userService.listByIdsIn(userIds).stream()
                .collect(Collectors.toMap(UserVO::getId, Function.identity(), (a, b) -> a));
        // 组装 VO
        return requests.stream().map(r -> {
            FriendRequestVO vo = new FriendRequestVO();
            BeanUtil.copyProperties(r, vo);
            vo.setFromUser(userMap.get(r.getFromUid()));
            vo.setToUser(userMap.get(r.getToUid()));
            return vo;
        }).collect(Collectors.toList());
    }

    /** 限流降级 → 抛 429；熔断/异常 → 透传业务异常 */
    private void sendFriendRequestFallback(Long toUid, String message, Throwable t) {
        if (t instanceof RequestNotPermitted) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS);
        }
        if (t instanceof BusinessException) {
            throw (BusinessException) t;
        }
        log.warn("[熔断] 好友请求降级: toUid={}, err={}", toUid, t.getMessage());
        throw new BusinessException(ResultCode.SYSTEM_ERROR, "服务暂不可用，请稍后重试");
    }

    /** accept/reject 降级：限流 → 429；其他异常 → 系统错误 */
    private void acceptRequestFallback(Long requestId, Throwable t) {
        if (t instanceof RequestNotPermitted) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS);
        }
        if (t instanceof BusinessException) {
            throw (BusinessException) t;
        }
        log.warn("[熔断] 好友请求处理降级: requestId={}, err={}", requestId, t.getMessage());
        throw new BusinessException(ResultCode.SYSTEM_ERROR, "服务暂不可用，请稍后重试");
    }

    /** 清除指定用户的好友列表缓存 */
    private void evictFriendListCache(Long userId) {
        Cache cache = cacheManager.getCache(FRIEND_LIST_CACHE);
        if (cache != null) {
            cache.evict(userId);
        }
    }

    /** 列表接口降级：限流 → 429；熔断 → 返回空列表（保前端可用） */
    private List<UserVO> listFallback(Throwable t) {
        if (t instanceof RequestNotPermitted) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS);
        }
        log.warn("[熔断] 好友列表降级: err={}", t.getMessage());
        return Collections.emptyList();
    }

}
