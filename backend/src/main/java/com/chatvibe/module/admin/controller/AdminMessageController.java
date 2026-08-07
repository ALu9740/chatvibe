package com.chatvibe.module.admin.controller;

import com.chatvibe.common.result.PageResult;
import com.chatvibe.common.result.Result;
import com.chatvibe.module.admin.dto.DeleteMessageDTO;
import com.chatvibe.module.admin.service.AdminMessageService;
import com.chatvibe.module.admin.vo.AuditMessageVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员消息审计接口
 *
 * @author Alu
 * @date 2026-08-07
 */
@RestController
@RequestMapping("/api/admin/messages")
@RequiredArgsConstructor
public class AdminMessageController {

    private final AdminMessageService adminMessageService;

    /**
     * 搜索/审计消息
     */
    @GetMapping("/search")
    public Result<PageResult<AuditMessageVO>> searchMessages(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long senderId,
            @RequestParam(required = false) Long conversationId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(adminMessageService.searchMessages(keyword, senderId, conversationId, type, startDate, endDate, page, size));
    }

    /**
     * 删除消息（逻辑删除）
     */
    @DeleteMapping("/{messageId}")
    public Result<Boolean> deleteMessage(@PathVariable Long messageId, @Valid @RequestBody DeleteMessageDTO dto) {
        return Result.success(adminMessageService.deleteMessage(messageId, dto.getReason()));
    }
}
