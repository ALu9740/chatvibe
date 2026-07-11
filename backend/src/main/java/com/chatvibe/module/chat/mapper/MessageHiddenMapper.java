package com.chatvibe.module.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chatvibe.module.chat.entity.MessageHidden;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消息隐藏 Mapper（用户级消息删除）
 *
 * @author Alu
 * @date 2026-06-29
 */
@Mapper
public interface MessageHiddenMapper extends BaseMapper<MessageHidden> {
}
