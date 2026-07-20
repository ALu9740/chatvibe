package com.chatvibe.module.friend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chatvibe.common.result.ResultCode;
import com.chatvibe.common.exception.BusinessException;
import com.chatvibe.module.chat.entity.Conversation;
import com.chatvibe.module.chat.entity.ConversationMember;
import com.chatvibe.module.chat.mapper.ConversationMapper;
import com.chatvibe.module.chat.mapper.ConversationMemberMapper;
import com.chatvibe.module.chat.service.ChatService;
import com.chatvibe.module.friend.entity.FriendRequest;
import com.chatvibe.module.friend.enums.FriedStatusEnum;
import com.chatvibe.module.friend.mapper.FriendRequestMapper;
import com.chatvibe.module.friend.service.FriendService;
import com.chatvibe.module.friend.vo.FriendRequestVO;
import com.chatvibe.module.notification.enums.NotificationTypeEnum;
import com.chatvibe.module.notification.service.NotificationService;
import com.chatvibe.module.user.entity.User;
import com.chatvibe.module.user.service.UserService;
import com.chatvibe.module.user.vo.UserVO;
import com.chatvibe.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
    private final NotificationService notificationService;

    @Override
    public List<UserVO> searchUsers(String keyword) {
        return userService.searchUsers(keyword);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendFriendRequest(Long toUid, String message) {
        Long fromUid = SecurityUtils.getCurrentUserId();
        if (fromUid.equals(toUid)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "不能添加自己为好友");
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
        // 通知对方：收到好友请求
        User fromUser = userService.getById(fromUid);
        String fromNickname = fromUser != null ? fromUser.getNickname() : "未知用户";
        String extra = new JSONObject()
                .set("fromUserId", fromUid)
                .set("fromNickname", fromNickname)
                .set("requestId", request.getId())
                .toString();
        notificationService.createNotification(toUid, NotificationTypeEnum.FRIEND_REQUEST,
                "好友请求", fromNickname + " 请求添加你为好友", extra);
    }

    @Override
    public List<FriendRequestVO> getReceivedRequests() {
        Long userId = SecurityUtils.getCurrentUserId();
        List<FriendRequest> requests = friendRequestMapper.selectList(
                new LambdaQueryWrapper<FriendRequest>()
                        .eq(FriendRequest::getToUid, userId)
                        .eq(FriendRequest::getStatus, FriedStatusEnum.STATUS_PENDING.getCode())
                        .orderByDesc(FriendRequest::getCreatedAt));
        return requests.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public List<FriendRequestVO> getSentRequests() {
        Long userId = SecurityUtils.getCurrentUserId();
        List<FriendRequest> requests = friendRequestMapper.selectList(
                new LambdaQueryWrapper<FriendRequest>()
                        .eq(FriendRequest::getFromUid, userId)
                        .orderByDesc(FriendRequest::getCreatedAt));
        return requests.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void acceptRequest(Long requestId) {
        Long userId = SecurityUtils.getCurrentUserId();
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
        // 更新请求状态
        request.setStatus(FriedStatusEnum.STATUS_ACCEPTED.getCode());
        friendRequestMapper.updateById(request);
        // 创建私聊会话
        chatService.createPrivateConversation(request.getFromUid(), request.getToUid());
        log.info("[好友] 接受好友请求: requestId={}, from={}, to={}",
                requestId, request.getFromUid(), request.getToUid());
        // 通知请求发起者：好友请求已接受
        User acceptUser = userService.getById(userId);
        String acceptNickname = acceptUser != null ? acceptUser.getNickname() : "未知用户";
        String extra = new JSONObject()
                .set("toUserId", request.getToUid())
                .toString();
        notificationService.createNotification(request.getFromUid(), NotificationTypeEnum.FRIEND_ACCEPT,
                "好友接受", acceptNickname + " 已接受你的好友请求", extra);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectRequest(Long requestId) {
        Long userId = SecurityUtils.getCurrentUserId();
        FriendRequest request = friendRequestMapper.selectById(requestId);
        if (request == null) {
            throw new BusinessException(ResultCode.FRIEND_REQUEST_NOT_FOUND);
        }
        if (!request.getToUid().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权处理此好友请求");
        }
        // 校验请求状态是否为待处理
        if (!Integer.valueOf(FriedStatusEnum.STATUS_PENDING.getCode()).equals(request.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "该请求已处理");
        }
        request.setStatus(FriedStatusEnum.STATUS_REJECTED.getCode());
        friendRequestMapper.updateById(request);
        log.info("[好友] 拒绝好友请求: requestId={}", requestId);
    }

    @Override
    public List<UserVO> getFriendList() {
        Long userId = SecurityUtils.getCurrentUserId();
        // 查询好友ID列表（原生SQL绕过 @TableLogic，删除会话不影响好友关系）
        List<Long> friendIds = conversationMemberMapper.selectFriendIdsIgnoreDeleted(userId);
        List<UserVO> friends = new ArrayList<>();
        for (Long friendId : friendIds) {
            User friend = userService.getById(friendId);
            if (friend != null) {
                friends.add(userService.toVO(friend));
            }
        }
        return friends;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFriend(Long friendId) {
        Long userId = SecurityUtils.getCurrentUserId();
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
        // 通知对方：好友关系已删除
        User currentUser = userService.getById(userId);
        String currentNickname = currentUser != null ? currentUser.getNickname() : "未知用户";
        String extra = new JSONObject()
                .set("fromUserId", userId)
                .toString();
        notificationService.createNotification(friendId, NotificationTypeEnum.FRIEND_DELETE,
                "好友删除", currentNickname + " 已删除与你的好友关系", extra);
    }

    /**
     * 转换为 VO (填充发起者/接收者信息)
     */
    private FriendRequestVO toVO(FriendRequest request) {
        FriendRequestVO vo = new FriendRequestVO();
        BeanUtil.copyProperties(request, vo);
        User fromUser = userService.getById(request.getFromUid());
        if (fromUser != null) {
            vo.setFromUser(userService.toVO(fromUser));
        }
        User toUser = userService.getById(request.getToUid());
        if (toUser != null) {
            vo.setToUser(userService.toVO(toUser));
        }
        return vo;
    }
}
