package com.chatvibe.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chatvibe.module.admin.entity.SystemConfig;
import com.chatvibe.module.admin.mapper.SystemConfigMapper;
import com.chatvibe.module.admin.vo.EmailConfigVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * 动态邮件发送器 Provider
 *
 * 核心机制：
 * 1. 启动时从 DB 加载邮件配置，DB 无配置则回退到 application.yml 中的 spring.mail.* 配置
 * 2. 管理员通过后台修改邮件配置后调用 reload() 重建 JavaMailSender，实时生效
 * 3. 所有邮件发送（验证码、密码重置通知、测试邮件）统一通过此 Provider 获取 JavaMailSender
 *
 * @author Alu
 * @date 2026-08-09
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicMailSenderProvider {

    private static final String CONFIG_KEY_EMAIL = "email_config";
    private static final String MASKED_PASSWORD = "******";

    private final SystemConfigMapper systemConfigMapper;
    private final ObjectMapper objectMapper;

    /** YAML 默认配置（回退用） */
    @Value("${spring.mail.host:}")
    private String yamlHost;

    @Value("${spring.mail.port:0}")
    private int yamlPort;

    @Value("${spring.mail.username:}")
    private String yamlUsername;

    @Value("${spring.mail.password:}")
    private String yamlPassword;

    @Value("${spring.mail.properties.mail.smtp.ssl.enable:false}")
    private boolean yamlSslEnabled;

    /** 当前运行时的 JavaMailSender 和发件地址 */
    private volatile JavaMailSender currentSender;
    private volatile String currentFromAddress;

    /**
     * 启动时初始化：先尝试从 DB 加载，回退到 YAML
     */
    @PostConstruct
    public void init() {
        EmailConfigVO dbConfig = loadFromDb();
        if (dbConfig != null && isValid(dbConfig)) {
            applyConfig(dbConfig);
            log.info("[邮件] 启动加载DB邮件配置: host={}, port={}", dbConfig.getHost(), dbConfig.getPort());
        } else {
            // 回退到 YAML 配置
            EmailConfigVO yamlConfig = buildFromYaml();
            applyConfig(yamlConfig);
            log.info("[邮件] 启动使用YAML默认邮件配置: host={}, port={}", yamlConfig.getHost(), yamlConfig.getPort());
        }
    }

    /**
     * 获取当前 JavaMailSender
     */
    public JavaMailSender getMailSender() {
        return currentSender;
    }

    /**
     * 获取当前发件地址
     */
    public String getFromAddress() {
        return currentFromAddress;
    }

    /**
     * 重新加载邮件配置（管理员修改后调用）
     */
    public synchronized void reload(EmailConfigVO config) {
        applyConfig(config);
        log.info("[邮件] 邮件配置已动态更新: host={}, port={}, ssl={}",
                config.getHost(), config.getPort(), config.getSslEnabled());
    }

    /**
     * 应用配置：创建新的 JavaMailSender 并更新发件地址
     */
    private void applyConfig(EmailConfigVO config) {
        JavaMailSenderImpl sender = createMailSender(config);
        this.currentSender = sender;
        this.currentFromAddress = resolveFromAddress(config);
    }

    /**
     * 根据 EmailConfigVO 创建 JavaMailSenderImpl（公开方法，测试邮件时也可调用）
     */
    public JavaMailSenderImpl createMailSender(EmailConfigVO config) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(config.getHost());
        sender.setPort(config.getPort());
        sender.setUsername(config.getUsername());
        sender.setPassword(config.getPassword());
        sender.setDefaultEncoding("UTF-8");

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        // 信任指定 host，避免 SSL 证书验证失败（QQ/163 等国内邮箱必需）
        props.put("mail.smtp.ssl.trust", config.getHost());

        boolean sslEnabled = config.getSslEnabled() != null && config.getSslEnabled();
        if (sslEnabled) {
            // SSL 模式（端口 465）
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            props.put("mail.smtp.socketFactory.port", String.valueOf(config.getPort()));
            props.put("mail.smtp.socketFactory.fallback", "false");
        } else {
            // STARTTLS 模式（端口 587）
            props.put("mail.smtp.starttls.enable", "true");
        }

        sender.setJavaMailProperties(props);
        return sender;
    }

    /**
     * 根据配置解析发件地址（fromEmail 优先，为空则用 username）
     */
    public static String resolveFromAddress(EmailConfigVO config) {
        if (config.getFromEmail() != null && !config.getFromEmail().isBlank()) {
            return config.getFromEmail();
        }
        return config.getUsername();
    }

    /**
     * 从 DB 加载邮件配置
     */
    private EmailConfigVO loadFromDb() {
        try {
            SystemConfig config = systemConfigMapper.selectOne(
                    new LambdaQueryWrapper<SystemConfig>()
                            .eq(SystemConfig::getConfigKey, CONFIG_KEY_EMAIL));
            if (config != null && config.getConfigValue() != null) {
                return objectMapper.readValue(config.getConfigValue(), EmailConfigVO.class);
            }
        } catch (Exception e) {
            log.warn("[邮件] 从DB加载邮件配置失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 从 YAML 属性构建配置（回退用）
     */
    private EmailConfigVO buildFromYaml() {
        EmailConfigVO vo = new EmailConfigVO();
        vo.setHost(yamlHost);
        vo.setPort(yamlPort);
        vo.setUsername(yamlUsername);
        vo.setPassword(yamlPassword);
        vo.setFromEmail(yamlUsername);
        vo.setSslEnabled(yamlSslEnabled);
        return vo;
    }

    /**
     * 检查配置是否有效
     */
    private boolean isValid(EmailConfigVO config) {
        return config.getHost() != null && !config.getHost().isBlank()
                && config.getPort() != null && config.getPort() > 0
                && config.getUsername() != null && !config.getUsername().isBlank();
    }
}
