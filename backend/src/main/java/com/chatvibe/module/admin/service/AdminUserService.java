package com.chatvibe.module.admin.service;

import com.chatvibe.common.result.PageResult;
import com.chatvibe.module.admin.vo.SystemUserVO;

/**
 * 管理员用户管理服务
 *
 * @author Alu
 * @date 2026-08-06
 */
public interface AdminUserService {

    /**
     * 分页查询用户列表
     *
     * @param keyword 关键字(邮箱/昵称)
     * @param status  状态筛选: normal/banned
     * @param role    角色筛选: USER/OPERATOR/ADMIN/SUPER_ADMIN
     * @param page    页码
     * @param size    每页大小
     * @return 用户列表分页结果
     */
    PageResult<SystemUserVO> getUserList(String keyword, String status, String role, int page, int size);

    /**
     * 封禁用户
     *
     * @param userId   用户ID
     * @param type     封禁类型: temp/permanent
     * @param duration 封禁时长
     * @param reason   封禁原因
     * @return 是否成功
     */
    boolean banUser(Long userId, String type, String duration, String reason);

    /**
     * 解封用户
     *
     * @param userId 用户ID
     * @return 是否成功
     */
    boolean unbanUser(Long userId);

    /**
     * 修改用户角色
     *
     * @param userId 用户ID
     * @param role   新角色: USER/OPERATOR/ADMIN/SUPER_ADMIN
     * @param reason 修改原因
     * @return 是否成功
     */
    boolean changeUserRole(Long userId, String role, String reason);

    /**
     * 重置用户密码
     *
     * @param userId 用户ID
     * @return 是否成功
     */
    boolean resetUserPassword(Long userId);
}
