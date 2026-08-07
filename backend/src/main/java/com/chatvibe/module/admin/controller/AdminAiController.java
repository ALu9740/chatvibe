package com.chatvibe.module.admin.controller;

import com.chatvibe.common.result.PageResult;
import com.chatvibe.common.result.Result;
import com.chatvibe.module.admin.dto.SaveAiProviderDTO;
import com.chatvibe.module.admin.service.AdminAiService;
import com.chatvibe.module.admin.vo.AiConversationMessageVO;
import com.chatvibe.module.admin.vo.AiConversationRecordVO;
import com.chatvibe.module.admin.vo.AiProviderVO;
import com.chatvibe.module.admin.vo.FailoverConfigVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理员 AI 服务管理接口
 *
 * @author Alu
 * @date 2026-08-07
 */
@RestController
@RequestMapping("/api/admin/ai")
@RequiredArgsConstructor
public class AdminAiController {

    private final AdminAiService adminAiService;

    /**
     * 获取所有 AI 供应商列表
     */
    @GetMapping("/providers")
    public Result<List<AiProviderVO>> getProviders() {
        return Result.success(adminAiService.getProviders());
    }

    /**
     * 新增 AI 供应商
     */
    @PostMapping("/providers")
    public Result<Boolean> addProvider(@Valid @RequestBody SaveAiProviderDTO dto) {
        return Result.success(adminAiService.addProvider(dto));
    }

    /**
     * 更新 AI 供应商
     */
    @PutMapping("/providers/{id}")
    public Result<Boolean> updateProvider(@PathVariable Long id,
                                           @Valid @RequestBody SaveAiProviderDTO dto) {
        return Result.success(adminAiService.updateProvider(id, dto));
    }

    /**
     * 删除 AI 供应商
     */
    @DeleteMapping("/providers/{id}")
    public Result<Boolean> deleteProvider(@PathVariable Long id) {
        return Result.success(adminAiService.deleteProvider(id));
    }

    /**
     * 测试供应商连接
     */
    @PostMapping("/providers/{id}/test")
    public Result<Map<String, Object>> testProvider(@PathVariable Long id) {
        return Result.success(adminAiService.testProvider(id));
    }

    /**
     * 获取故障转移配置
     */
    @GetMapping("/failover")
    public Result<FailoverConfigVO> getFailoverConfig() {
        return Result.success(adminAiService.getFailoverConfig());
    }

    /**
     * 更新故障转移配置
     */
    @PutMapping("/failover")
    public Result<Boolean> updateFailoverConfig(@RequestBody FailoverConfigVO config) {
        return Result.success(adminAiService.updateFailoverConfig(config));
    }

    /**
     * 分页查询 AI 会话记录（支持按用户搜索、按供应商筛选）
     */
    @GetMapping("/conversations")
    public Result<PageResult<AiConversationRecordVO>> getAiConversations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String provider) {
        return Result.success(adminAiService.getAiConversations(page, size, search, provider));
    }

    /**
     * 获取 AI 对话消息详情（管理员只读）
     */
    @GetMapping("/conversations/{conversationId}/messages")
    public Result<List<AiConversationMessageVO>> getAiConversationMessages(
            @PathVariable Long conversationId) {
        return Result.success(adminAiService.getAiConversationMessages(conversationId));
    }
}
