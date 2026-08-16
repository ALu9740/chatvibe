package com.chatvibe.module.auth.event;

import com.chatvibe.config.DynamicMailSenderProvider;
import com.chatvibe.config.RabbitMQConfig;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 用户封禁/解封消息事件消费者
 * 异步发送封禁/解封通知邮件
 *
 * @author Alu
 * @date 2026-08-16
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserBanEventConsumer {
    private final DynamicMailSenderProvider mailSenderProvider;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @RabbitListener(queues = RabbitMQConfig.USER_BAN_QUEUE)
    @CircuitBreaker(name = "banService", fallbackMethod = "fallbackNotifyEmail")
    public void handleBanEvent(UserBanEvent event) {
        try {
            MimeMessage mimeMessage = mailSenderProvider.getMailSender().createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(mailSenderProvider.getFromAddress());
            helper.setTo(event.getEmail());

            String timeStr = LocalDateTime.now().format(DATE_TIME_FORMATTER);

            if (event.isBanned()) {
                // 封禁邮件
                helper.setSubject("【ChatVibe】您的账号已被封禁");

                String durationDesc = event.getDuration() != null ? event.getDuration() : "永久";
                String typeDesc = "permanent".equals(event.getType()) ? "永久封禁" : "临时封禁";

                String html = """
                        <!DOCTYPE html>
                        <html>
                        <head><meta charset="UTF-8"></head>
                        <body style="font-family: 'Helvetica Neue', Arial, sans-serif; background:#F7F9FC; padding:24px;">
                          <div style="max-width:480px; margin:0 auto; background:#fff; border-radius:16px; overflow:hidden; box-shadow:0 8px 24px rgba(15,23,42,0.08);">
                            <div style="background:linear-gradient(135deg,#DC2626 0%%,#EF4444 45%%,#F87171 100%%); padding:32px 24px; text-align:center;">
                              <h1 style="color:#fff; margin:0; font-size:24px; letter-spacing:0.5px;">ChatVibe</h1>
                              <p style="color:rgba(255,255,255,0.85); font-size:12px; margin:6px 0 0;">让沟通更有温度</p>
                            </div>
                            <div style="padding:32px 24px;">
                              <h2 style="color:#0F172A; font-size:18px; margin:0 0 16px;">账号封禁通知</h2>
                              <p style="color:#475569; font-size:14px; line-height:1.6;">您好 %s，您的 ChatVibe 账号已被管理员封禁。</p>
                              <div style="background:#FEF2F2; border:1px solid #FECACA; border-radius:10px; padding:16px 20px; margin:16px 0;">
                                <p style="color:#64748B; font-size:12px; margin:0 0 6px;">封禁类型：</p>
                                <p style="color:#DC2626; font-size:14px; font-weight:600; margin:0 0 12px;">%s（%s）</p>
                                <p style="color:#64748B; font-size:12px; margin:0 0 6px;">封禁原因：</p>
                                <p style="color:#0F172A; font-size:14px; margin:0;">%s</p>
                              </div>
                              <p style="color:#475569; font-size:14px; line-height:1.6;">如有疑问，请联系管理员：alu9740@163.com。</p>
                              <p style="color:#999; font-size:12px; margin-top:24px;">封禁时间：%s</p>
                            </div>
                            <div style="background:#fafafa; padding:16px 24px; text-align:center; border-top:1px solid #eee;">
                              <p style="color:#bbb; font-size:12px; margin:0;">© ChatVibe · 本邮件由系统自动发送，请勿回复</p>
                            </div>
                          </div>
                        </body>
                        </html>
                        """.formatted(
                        event.getNickname() != null ? event.getNickname() : "用户",
                        typeDesc, durationDesc,
                        event.getReason() != null ? event.getReason() : "未提供",
                        timeStr
                );

                helper.setText(html, true);
            } else {
                // 解封邮件
                helper.setSubject("【ChatVibe】您的账号已被解封");

                String html = """
                        <!DOCTYPE html>
                        <html>
                        <head><meta charset="UTF-8"></head>
                        <body style="font-family: 'Helvetica Neue', Arial, sans-serif; background:#F7F9FC; padding:24px;">
                          <div style="max-width:480px; margin:0 auto; background:#fff; border-radius:16px; overflow:hidden; box-shadow:0 8px 24px rgba(15,23,42,0.08);">
                            <div style="background:linear-gradient(135deg,#059669 0%%,#10B981 45%%,#34D399 100%%); padding:32px 24px; text-align:center;">
                              <h1 style="color:#fff; margin:0; font-size:24px; letter-spacing:0.5px;">ChatVibe</h1>
                              <p style="color:rgba(255,255,255,0.85); font-size:12px; margin:6px 0 0;">让沟通更有温度</p>
                            </div>
                            <div style="padding:32px 24px;">
                              <h2 style="color:#0F172A; font-size:18px; margin:0 0 16px;">账号解封通知</h2>
                              <p style="color:#475569; font-size:14px; line-height:1.6;">您好 %s，您的 ChatVibe 账号已被管理员解封，现在可以正常登录使用了。</p>
                              <p style="color:#475569; font-size:14px; line-height:1.6;">感谢您的耐心等待，请遵守社区规范，文明使用。</p>
                              <p style="color:#999; font-size:12px; margin-top:24px;">解封时间：%s</p>
                            </div>
                            <div style="background:#fafafa; padding:16px 24px; text-align:center; border-top:1px solid #eee;">
                              <p style="color:#bbb; font-size:12px; margin:0;">© ChatVibe · 本邮件由系统自动发送，请勿回复</p>
                            </div>
                          </div>
                        </body>
                        </html>
                        """.formatted(
                        event.getNickname() != null ? event.getNickname() : "用户",
                        timeStr
                );

                helper.setText(html, true);
            }

            mailSenderProvider.getMailSender().send(mimeMessage);
            log.info("[MQ] 封禁/解封通知邮件已发送: email={}, banned={}", event.getEmail(), event.isBanned());
        } catch (MessagingException e) {
            log.error("[MQ] 封禁/解封通知邮件发送失败: email={}", event.getEmail(), e);
            throw new RuntimeException("封禁/解封通知邮件发送失败", e);
        }
    }

    /** 熔断降级：仅记录日志，不影响主流程 */
    public void fallbackNotifyEmail(UserBanEvent event, Exception e) {
        log.warn("[熔断] 封禁/解封通知邮件降级: userId={}, banned={}, reason={}",
                event.getUserId(), event.isBanned(), e.getMessage());
    }
}
