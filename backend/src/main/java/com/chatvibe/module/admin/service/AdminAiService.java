package com.chatvibe.module.admin.service;

import com.chatvibe.common.result.PageResult;
import com.chatvibe.module.admin.dto.SaveAiProviderDTO;
import com.chatvibe.module.admin.vo.AiConversationMessageVO;
import com.chatvibe.module.admin.vo.AiConversationRecordVO;
import com.chatvibe.module.admin.vo.AiProviderVO;
import com.chatvibe.module.admin.vo.FailoverConfigVO;

import java.util.List;
import java.util.Map;

/**
 * 管理员 AI 服务管理接口
 *
 * @author Alu
 * @date 2026-08-07
 */
public interface AdminAiService {

    /**
     * 获取所有 AI 供应商列表
     *
     * @return 供应商列表(apiKey 已脱敏)
     */
    List<AiProviderVO> getProviders();

    /**
     * 新增 AI 供应商
     *
     * @param dto 供应商信息
     * @return 是否成功
     */
    boolean addProvider(SaveAiProviderDTO dto);

    /**
     * 更新 AI 供应商
     *
     * @param id  供应商ID
     * @param dto 供应商信息
     * @return 是否成功
     */
    boolean updateProvider(Long id, SaveAiProviderDTO dto);

    /**
     * 删除 AI 供应商(逻辑删除)
     *
     * @param id 供应商ID
     * @return 是否成功
     */
    boolean deleteProvider(Long id);

    /**
     * 测试供应商连接
     *
     * @param id 供应商ID
     * @return 测试结果: success, latency, message
     */
    Map<String, Object> testProvider(Long id);

    /**
     * 获取故障转移配置
     *
     * @return 故障转移配置
     */
    FailoverConfigVO getFailoverConfig();

    /**
     * 更新故障转移配置
     *
     * @param config 故障转移配置
     * @return 是否成功
     */
    boolean updateFailoverConfig(FailoverConfigVO config);

    /**
     * 分页查询 AI 会话记录（支持按用户搜索、按供应商筛选）
     *
     * @param page     页码
     * @param size     每页大小
     * @param search   用户搜索关键词（用户ID或昵称，可为空）
     * @param provider 供应商筛选（可为空）
     * @return AI 会话分页结果
     */
    PageResult<AiConversationRecordVO> getAiConversations(int page, int size, String search, String provider);

    /**
     * 获取 AI 对话消息详情（管理员只读）
     * 最多返回 200 条消息（对齐 GET /ai/conversations/{id}/messages 限制）
     *
     * @param conversationId AI 会话ID
     * @return 消息列表
     */
    List<AiConversationMessageVO> getAiConversationMessages(Long conversationId);
}
