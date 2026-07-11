package com.chatvibe.module.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chatvibe.module.ai.entity.AiConversation;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 会话 Mapper
 *
 * @author Alu
 * @date 2026-07-01
 */
@Mapper
public interface AiConversationMapper extends BaseMapper<AiConversation> {
}
