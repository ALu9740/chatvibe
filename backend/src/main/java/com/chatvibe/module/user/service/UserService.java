package com.chatvibe.module.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chatvibe.module.user.dto.ChangeEmailDTO;
import com.chatvibe.module.user.dto.ChangePasswordDTO;
import com.chatvibe.module.user.dto.UpdateProfileDTO;
import com.chatvibe.module.user.entity.User;
import com.chatvibe.module.user.vo.NotificationPreferencesVO;
import com.chatvibe.module.user.vo.UserVO;

import java.util.List;

/**
 * 用户服务接口
 *
 * @author Alu
 * @date 2026-06-27
 */
public interface UserService extends IService<User> {

    /**
     * 获取用户在线状态
     *
     * @param userId 用户ID
     * @return 在线状态值：0-离线 1-在线 2-忙碌 3-离开
     */
    Integer getUserStatus(Long userId);

    /**
     * 获取当前登录用户资料
     *
     * @return 用户资料
     */
    UserVO getCurrentUserInfo();

    /**
     * 根据ID获取用户资料
     *
     * @param userId 用户ID
      * @return 用户资料
     */
    UserVO getUserInfo(Long userId);

    /**
     * 更新当前用户资料
     *
     * @param dto 更新资料DTO
     * @return 更新后的用户资料
     */
    UserVO updateProfile(UpdateProfileDTO dto);

    /**
     * 修改密码
     *
     * @param dto 修改密码DTO
     */
    void changePassword(ChangePasswordDTO dto);

    /**
     * 头像上传(stub)
     *
     * @param base64 头像图片的 base64 编码
     * @return 头像图片的 URL
     */
    String uploadAvatar(String base64);

    /**
     * 搜索用户
     *
     * @param keyword 搜索关键词
     * @return 用户列表
     */
    List<UserVO> searchUsers(String keyword);

    /**
     * 根据邮箱查询用户
     */
    User findByEmail(String email);

    /**
     * 实体转 VO
     */
    UserVO toVO(User user);

    /**
     * 更新在线状态（写库 + 广播）
     * 用于：登录置在线、登出置离线、WebSocket 上下线（已不再触发）等场景
     *
     * @param userId 用户ID
     * @param status 状态值
     */
    void updateStatus(Long userId, Integer status);

    /**
     * 用户手动更新在线状态
     *
     * @param userId 用户ID
     * @param status 状态值
     */
    void updateStatusManually(Long userId, Integer status);

    /**
     * 更换绑定邮箱
     *
     * @param dto 更换绑定邮箱DTO
     */
    void changeEmail(ChangeEmailDTO dto);

    /**
     * 获取通知偏好
     *
     * @return 通知偏好VO
     */
    NotificationPreferencesVO getNotificationPreferences();

    /**
     * 更新通知偏好
     *
     * @param vo 通知偏好VO
     */
    void updateNotificationPreferences(NotificationPreferencesVO vo);
}
