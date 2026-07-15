package com.chatvibe.module.auth.event;

import com.chatvibe.config.RabbitMQConfig;
import com.chatvibe.module.chat.service.ChatService;
import com.chatvibe.module.file.service.FileStorageService;
import com.chatvibe.module.user.mapper.UserMapper;
import com.chatvibe.module.user.util.AvatarGenerator;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 注册消息事件消费者
 *
 * @author Alu
 * @date 2026-07-15
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserRegisterEventConsumer {
    private final ChatService chatService;
    private final FileStorageService fileStorageService;
    private final UserMapper userMapper;

    /**
     * 消费注册事件：异步创建 AI 会话
     * 使用 Resilience4j 熔断器保护，持续失败时熔断
     */
    @RabbitListener(queues = RabbitMQConfig.USER_REGISTER_QUEUE)
    @CircuitBreaker(name = "registerService", fallbackMethod = "fallbackCreateAiConversation")
    public void handleRegisterEvent(UserRegisterEvent event) {
        log.info("[MQ] 消费注册事件，创建AI会话: userId={}", event.getUserId());
        // 1. 生成默认头像并上传 MinIO
        try {
            byte[] avatarBytes = AvatarGenerator.generate(event.getNickname());
            String avatarUrl = fileStorageService.upload(
                    avatarBytes,
                    "user_avatar/default",
                    "avatar_" + event.getUserId() + ".png",
                    "image/png"
            );
            // 更新用户头像
            userMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<com.chatvibe.module.user.entity.User>()
                    .eq(com.chatvibe.module.user.entity.User::getId, event.getUserId())
                    .set(com.chatvibe.module.user.entity.User::getAvatar, avatarUrl));
            log.info("[MQ] 默认头像已生成: userId={}, url={}", event.getUserId(), avatarUrl);
        } catch (Exception e) {
            log.warn("[MQ] 默认头像生成失败: userId={}, err={}", event.getUserId(), e.getMessage());
        }

        // 2. 创建 AI 会话
        chatService.createAiConversation(event.getUserId());
    }

    /**
     * 熔断降级方法：记录日志，不影响用户注册
     */
    public void fallbackCreateAiConversation(UserRegisterEvent event, Exception e) {
        log.warn("[熔断] AI会话创建降级: userId={}, reason={}", event.getUserId(), e.getMessage());
        // 用户可在首次进入聊天时懒加载创建 AI 会话
    }
}
