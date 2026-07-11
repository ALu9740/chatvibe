package com.chatvibe.module.group.service;

import com.chatvibe.module.chat.vo.ConversationVO;
import com.chatvibe.module.group.dto.CreateGroupDTO;
import com.chatvibe.module.group.vo.GroupMemberVO;

import java.util.List;

/**
 * 群组服务接口
 *
 * @author Alu
 * @date 2026-07-01
 */
public interface GroupService {

    /**
     * 创建群组
     *
     * @param dto 创建群组 DTO
     * @return 群组详情
     */
    ConversationVO createGroup(CreateGroupDTO dto);

    /**
     * 获取群详情
     *
     * @param groupId 群组ID
     * @return 群组详情
     */
    ConversationVO getGroupDetail(Long groupId);

    /**
     * 编辑群信息
     *
     * @param groupId 群组ID
     * @param name    群组名称
     * @param avatar  群组头像
     * @return 群组详情
     */
    ConversationVO updateGroup(Long groupId, String name, String avatar);

    /**
     * 获取群成员列表（含群内角色）
     */
    List<GroupMemberVO> getGroupMembers(Long groupId);

    /**
     * 邀请成员
     *
     * @param groupId   群组ID
     * @param memberIds 成员ID列表
     */
    void inviteMembers(Long groupId, List<Long> memberIds);

    /**
     * 移除成员
     *
     * @param groupId 群组ID
     * @param userId  被移除用户ID
     */
    void removeMember(Long groupId, Long userId);

    /**
     * 退出群组
     *
     * @param groupId 群组ID
     */
    void leaveGroup(Long groupId);

    /**
     * 转让群主
     *
     * @param groupId    群组ID
     * @param newOwnerId 新群主用户ID
     */
    void transferOwner(Long groupId, Long newOwnerId);

    /**
     * 解散群组
     *
     * @param groupId 群组ID
     */
    void dissolveGroup(Long groupId);
}
