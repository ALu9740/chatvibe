package com.chatvibe.module.friend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chatvibe.module.friend.entity.FriendRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * 好友请求 Mapper
 *
 * @author Alu
 * @date 2026-06-28
 */
@Mapper
public interface FriendRequestMapper extends BaseMapper<FriendRequest> {
    /**
     * 按ID批量查询好友请求(仅查询展示字段)
     */
    List<FriendRequest> selectByIdsIn(@Param("ids") Collection<Long> ids);
}
