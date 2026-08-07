package com.chatvibe.module.admin.service;

import com.chatvibe.common.result.PageResult;
import com.chatvibe.module.admin.vo.SystemGroupVO;

/**
 * 管理员群组管理服务
 *
 * @author Alu
 * @date 2026-08-07
 */
public interface AdminGroupService {

    /**
     * 分页查询系统群组列表
     *
     * @param keyword 关键字(群名称模糊匹配)
     * @param ownerId 群主ID
     * @param status  状态: normal-正常 dissolved-已解散
     * @param page    页码
     * @param size    每页大小
     * @return 群组分页结果
     */
    PageResult<SystemGroupVO> getGroupList(String keyword, Long ownerId, String status, int page, int size);

    /**
     * 解散群组
     *
     * @param groupId 群组ID
     * @param reason  解散原因
     * @return 是否成功
     */
    boolean dissolveGroup(Long groupId, String reason);
}
