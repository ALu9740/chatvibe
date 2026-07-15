package com.chatvibe.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chatvibe.module.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户 Mapper
 *
 * @author Alu
 * @date 2026-06-27
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 原子递增登录版本号并更新在线状态（避免读改写竞态）
     * @return 受影响行数
     */
    int incrLoginVersionAndSetOnline(@Param("userId") Long userId, @Param("status") Integer status);

    /**
     * 查询当前登录版本号
     */
    Integer selectLoginVersion(@Param("userId") Long userId);

    /**
     * 根据邮箱查询用户
     *
     * @param email 邮箱
     * @return 用户
     */
    User findByEmail(@Param("email") String email);

    /**
     * 搜索用户(排除指定ID)
     *
     * @param keyword   关键字
     * @param excludeId 排除的用户ID
     * @return 用户列表
     */
    List<User> searchUsers(@Param("keyword") String keyword, @Param("excludeId") Long excludeId);
}
