package com.chatvibe.module.chat.controller;

import com.chatvibe.common.result.Result;
import com.chatvibe.module.chat.dto.CreatePrivateConversationDTO;
import com.chatvibe.module.chat.dto.SendMessageDTO;
import com.chatvibe.module.chat.entity.Message;
import com.chatvibe.module.chat.service.ChatService;
import com.chatvibe.module.chat.vo.ConversationVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 聊天接口
 *
 * @author Alu
 * @date 2026-06-28
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * 获取当前用户的会话列表
     */
    @GetMapping("/conversations")
    public Result<List<ConversationVO>> getConversations() {
        return Result.success(chatService.getConversationList());
    }

    /**
     * 获取会话历史消息(分页)
     *
     * @param conversationId 会话ID
     * @param lastId         上一页最后一条消息ID(游标分页)
     * @param size           每页大小
     */
    @GetMapping("/messages/{conversationId}")
    public Result<List<Message>> getMessages(@PathVariable Long conversationId,
                                             @RequestParam(required = false) Long lastId,
                                             @RequestParam(defaultValue = "20") int size) {
        return Result.success(chatService.getHistoryMessages(conversationId, lastId, size));
    }

    /**
     * 发送消息
     */
    @PostMapping("/message")
    public Result<Message> sendMessage(@Valid @RequestBody SendMessageDTO dto) {
        return Result.success(chatService.sendMessage(dto));
    }

    /**
     * 标记会话已读
     */
    @PutMapping("/read/{conversationId}")
    public Result<Void> markAsRead(@PathVariable Long conversationId) {
        chatService.markAsRead(conversationId);
        return Result.success();
    }

    /**
     * 切换会话消息免打扰
     * 返回切换后的免打扰状态：true-已免打扰 false-已取消
     */
    @PutMapping("/mute/{conversationId}")
    public Result<Boolean> toggleMute(@PathVariable Long conversationId) {
        return Result.success(chatService.toggleMute(conversationId));
    }

    /**
     * 切换会话置顶
     * 返回切换后的置顶状态：true-已置顶 false-已取消
     */
    @PutMapping("/pin/{conversationId}")
    public Result<Boolean> togglePin(@PathVariable Long conversationId) {
        return Result.success(chatService.togglePin(conversationId));
    }

    /**
     * 创建或获取私聊会话
     */
    @PostMapping("/conversations/private")
    public Result<ConversationVO> createPrivateConversation(@Valid @RequestBody CreatePrivateConversationDTO dto) {
        return Result.success(chatService.createOrGetPrivateConversation(dto.getTargetUserId()));
    }

    /**
     * 删除/退出会话(从当前用户会话列表移除)
     */
    @DeleteMapping("/conversations/{conversationId}")
    public Result<Boolean> deleteConversation(@PathVariable Long conversationId) {
        chatService.deleteConversation(conversationId);
        return Result.success(true);
    }

    /**
     * 隐藏（删除）单条消息（仅对当前用户隐藏，其他用户仍可见）
     */
    @DeleteMapping("/messages/{messageId}")
    public Result<Boolean> hideMessage(@PathVariable Long messageId) {
        chatService.hideMessage(messageId);
        return Result.success(true);
    }

    /**
     * 获取当前用户的群组会话列表（含已从会话列表删除但未退出群组的）
     */
    @GetMapping("/conversations/groups/my")
    public Result<List<ConversationVO>> getMyGroupConversations() {
        return Result.success(chatService.getMyGroupConversations());
    }

    /**
     * 重新加入群聊会话（恢复会话列表中的显示）
     */
    @PostMapping("/conversations/{conversationId}/rejoin")
    public Result<ConversationVO> rejoinConversation(@PathVariable Long conversationId) {
        return Result.success(chatService.rejoinGroupConversation(conversationId));
    }

    /**
     * 清空当前用户在指定会话中的聊天记录（仅对当前用户隐藏，其他成员仍可见）
     */
    @DeleteMapping("/conversations/{conversationId}/history")
    public Result<Boolean> clearHistory(@PathVariable Long conversationId) {
        chatService.clearHistory(conversationId);
        return Result.success(true);
    }
}
