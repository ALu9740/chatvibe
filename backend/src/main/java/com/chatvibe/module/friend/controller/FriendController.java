package com.chatvibe.module.friend.controller;

import com.chatvibe.common.result.Result;
import com.chatvibe.module.friend.service.FriendService;
import com.chatvibe.module.friend.vo.FriendRequestVO;
import com.chatvibe.module.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 好友接口
 *
 * @author Alu
 * @date 2026-06-28
 */
@RestController
@RequestMapping("/api/friend")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;

    /**
     * 搜索用户
     */
    @GetMapping("/search")
    public Result<List<UserVO>> searchUsers(@RequestParam String keyword) {
        return Result.success(friendService.searchUsers(keyword));
    }

    /**
     * 发送好友请求
     */
    @PostMapping("/request")
    public Result<Void> sendFriendRequest(@RequestParam Long toUid,
                                          @RequestParam(required = false) String message) {
        friendService.sendFriendRequest(toUid, message);
        return Result.success();
    }

    /**
     * 获取收到的好友请求列表
     */
    @GetMapping("/requests/received")
    public Result<List<FriendRequestVO>> getReceivedRequests() {
        return Result.success(friendService.getReceivedRequests());
    }

    /**
     * 获取发送的好友请求列表
     */
    @GetMapping("/requests/sent")
    public Result<List<FriendRequestVO>> getSentRequests() {
        return Result.success(friendService.getSentRequests());
    }

    /**
     * 接受好友请求
     */
    @PutMapping("/request/{requestId}/accept")
    public Result<Void> acceptRequest(@PathVariable Long requestId) {
        friendService.acceptRequest(requestId);
        return Result.success();
    }

    /**
     * 拒绝好友请求
     */
    @PutMapping("/request/{requestId}/reject")
    public Result<Void> rejectRequest(@PathVariable Long requestId) {
        friendService.rejectRequest(requestId);
        return Result.success();
    }

    /**
     * 好友列表
     */
    @GetMapping("/list")
    public Result<List<UserVO>> getFriendList() {
        return Result.success(friendService.getFriendList());
    }

    /**
     * 删除好友
     */
    @DeleteMapping("/{friendId}")
    public Result<Boolean> deleteFriend(@PathVariable Long friendId) {
        friendService.deleteFriend(friendId);
        return Result.success(true);
    }
}
