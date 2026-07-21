package com.chatvibe.module.group.controller;

import com.chatvibe.common.result.Result;
import com.chatvibe.module.chat.vo.ConversationVO;
import com.chatvibe.module.group.dto.CreateGroupDTO;
import com.chatvibe.module.group.service.GroupService;
import com.chatvibe.module.group.vo.GroupMemberVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 群组接口
 *
 * @author Alu
 * @date 2026-06-28
 */
@RestController
@RequestMapping("/api/group")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    /**
     * 创建群组
     */
    @PostMapping
    public Result<ConversationVO> createGroup(@Valid @RequestBody CreateGroupDTO dto) {
        return Result.success(groupService.createGroup(dto));
    }

    /**
     * 获取群详情
     */
    @GetMapping("/{groupId}")
    public Result<ConversationVO> getGroupDetail(@PathVariable Long groupId) {
        return Result.success(groupService.getGroupDetail(groupId));
    }

    /**
     * 编辑群信息
     */
    @PutMapping("/{groupId}")
    public Result<ConversationVO> updateGroup(@PathVariable Long groupId,
                                              @RequestParam(required = false) String name,
                                              @RequestParam(required = false) String avatar) {
        return Result.success(groupService.updateGroup(groupId, name, avatar));
    }

    /**
     * 上传群头像（base64 → MinIO）
     */
    @PostMapping("/{groupId}/avatar")
    public Result<String> uploadGroupAvatar(@PathVariable Long groupId,
                                            @RequestBody java.util.Map<String, String> body) {
        String base64 = body.get("base64");
        return Result.success(groupService.uploadGroupAvatar(groupId, base64));
    }

    /**
     * 获取群成员列表（含群内角色）
     */
    @GetMapping("/{groupId}/members")
    public Result<List<GroupMemberVO>> getGroupMembers(@PathVariable Long groupId) {
        return Result.success(groupService.getGroupMembers(groupId));
    }

    /**
     * 邀请成员
     */
    @PostMapping("/{groupId}/members")
    public Result<Void> inviteMembers(@PathVariable Long groupId,
                                      @RequestBody List<Long> memberIds) {
        groupService.inviteMembers(groupId, memberIds);
        return Result.success();
    }

    /**
     * 移除成员
     */
    @DeleteMapping("/{groupId}/members/{userId}")
    public Result<Void> removeMember(@PathVariable Long groupId, @PathVariable Long userId) {
        groupService.removeMember(groupId, userId);
        return Result.success();
    }

    /**
     * 退出群组
     */
    @DeleteMapping("/{groupId}/leave")
    public Result<Void> leaveGroup(@PathVariable Long groupId) {
        groupService.leaveGroup(groupId);
        return Result.success();
    }

    /**
     * 转让群主
     */
    @PutMapping("/{groupId}/owner")
    public Result<Boolean> transferOwner(@PathVariable Long groupId,
                                         @RequestParam Long newOwnerId) {
        groupService.transferOwner(groupId, newOwnerId);
        return Result.success(true);
    }

    /**
     * 解散群组
     */
    @DeleteMapping("/{groupId}")
    public Result<Boolean> dissolveGroup(@PathVariable Long groupId) {
        groupService.dissolveGroup(groupId);
        return Result.success(true);
    }
}
