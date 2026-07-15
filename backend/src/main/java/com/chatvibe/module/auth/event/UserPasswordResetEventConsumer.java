package com.chatvibe.module.auth.event;

import com.chatvibe.config.RabbitMQConfig;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 密码重置消息事件消费者
 * 异步发送"密码已重置"通知邮件
 *
 * @author Alu
 * @date 2026-07-15
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserPasswordResetEventConsumer {
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String mailFrom;

    @RabbitListener(queues = RabbitMQConfig.USER_PASSWORD_RESET_QUEUE)
    @CircuitBreaker(name = "passwordResetService", fallbackMethod = "fallbackNotifyEmail")
    public void handlePasswordResetEvent(UserPasswordResetEvent event) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(mailFrom);
            helper.setTo(event.getEmail());
            helper.setSubject("【ChatVibe】您的密码已重置");

            // 获取当前时间字符串
            String timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            // 构建新的 HTML 内容
            String html = """
                    <!DOCTYPE html>
                    <html>
                    <head><meta charset="UTF-8"></head>
                    <body style="font-family: 'Helvetica Neue', Arial, sans-serif; background:#F7F9FC; padding:24px;">
                      <div style="max-width:480px; margin:0 auto; background:#fff; border-radius:16px; overflow:hidden; box-shadow:0 8px 24px rgba(15,23,42,0.08);">
                        <!-- 头部：蓝色渐变背景 -->
                        <div style="background:linear-gradient(135deg,#1E40AF 0%%,#2563EB 45%%,#0EA5E9 100%%); padding:32px 24px; text-align:center;">
                          <h1 style="color:#fff; margin:0; font-size:24px; letter-spacing:0.5px;">ChatVibe</h1>
                          <p style="color:rgba(255,255,255,0.85); font-size:12px; margin:6px 0 0;">让沟通更有温度</p>
                        </div>
                        
                        <!-- 主体内容 -->
                        <div style="padding:32px 24px;">
                          <h2 style="color:#0F172A; font-size:18px; margin:0 0 16px;">密码重置通知</h2>
                          <p style="color:#475569; font-size:14px; line-height:1.6;">您好，您的 ChatVibe 账号密码已成功重置。</p>
                          <p style="color:#475569; font-size:14px; line-height:1.6;">如果此操作非您本人发起，请立即登录并修改密码，或联系管理员：alu9740@163.com。</p>
                          <p style="color:#999; font-size:12px; margin-top:24px;">操作时间：%s</p>
                        </div>
                        
                        <!-- 底部：版权信息 -->
                        <div style="background:#fafafa; padding:16px 24px; text-align:center; border-top:1px solid #eee;">
                          <p style="color:#bbb; font-size:12px; margin:0;">© ChatVibe · 本邮件由系统自动发送，请勿回复</p>
                        </div>
                      </div>
                    </body>
                    </html>
                    """.formatted(timeStr);

            helper.setText(html, true);
            mailSender.send(mimeMessage);
            log.info("[MQ] 密码重置通知邮件已发送: email={}", event.getEmail());
        } catch (MessagingException e) {
            log.error("[MQ] 密码重置通知邮件发送失败: email={}", event.getEmail(), e);
            throw new RuntimeException("密码重置通知邮件发送失败", e);
        }
    }

    /** 熔断降级：仅记录日志，不影响主流程 */
    public void fallbackNotifyEmail(UserPasswordResetEvent event, Exception e) {
        log.warn("[熔断] 密码重置通知邮件降级: userId={}, reason={}",
                event.getUserId(), e.getMessage());
    }
}