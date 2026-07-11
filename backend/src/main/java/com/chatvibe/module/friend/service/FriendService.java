package com.chatvibe.module.friend.service;

import com.chatvibe.module.friend.vo.FriendRequestVO;
import com.chatvibe.module.user.vo.UserVO;

import java.util.List;

/**
 * 好友服务接口
 *
 * @author Alu
 * @date 2026-06-28
 */
public interface FriendService {

    /**
     * 搜索用户
     *
     * @param keyword 关键字
     */
    List<UserVO> searchUsers(String keyword);

    /**
     * 发送好友请求
     *
     * @param toUid   接收者ID
     * @param message 验证消息
     */
    void sendFriendRequest(Long toUid, String message);

    /**
     * 获取收到的好友请求列表
     *
      * @return 好友请求列表
     */
    List<FriendRequestVO> getReceivedRequests();

    /**
     * 获取发送的好友请求列表
     *
      * @return 好友请求列表
     */
    List<FriendRequestVO> getSentRequests();

    /**
     * 接受好友请求
     *
     * @param requestId 请求ID
     */
    void acceptRequest(Long requestId);

    /**
     * 拒绝好友请求
     *
     * @param requestId 请求ID
     */
    void rejectRequest(Long requestId);

    /**
     * 好友列表
     */
    List<UserVO> getFriendList();

    /**
     * 删除好友
     *
     * @param friendId 好友用户ID
     */
    void deleteFriend(Long friendId);
}
