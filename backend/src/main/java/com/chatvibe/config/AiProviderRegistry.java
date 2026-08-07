package com.chatvibe.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chatvibe.module.admin.entity.AiProvider;
import com.chatvibe.module.admin.entity.SystemConfig;
import com.chatvibe.module.admin.mapper.AiProviderMapper;
import com.chatvibe.module.admin.mapper.SystemConfigMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 供应商动态注册中心
 * <p>
 * 替代原 YAML 硬编码的供应商配置，所有配置由数据库驱动。
 * 启动时从数据库加载已启用的供应商配置，构建独立的 ChatClient 列表。
 * 管理员通过后台修改供应商配置后，调用 {@link #refresh()} 重建列表，无需重启。
 *
 * @author Alu
 * @date 2026-08-07
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiProviderRegistry {

    private static final String AI_FAILOVER_ENABLED_KEY = "ai_failover_enabled";

    /**
     * 系统提示词：定义 AI 身份为 vibe 助手
     */
    public static final String SYSTEM_PROMPT =
            "你是vibe助手，由Alu打造的一款智能助手。你正在一个即时通讯系统中与用户聊天。\n"
                    + "你可以帮助用户处理工作事务、生成文案、总结内容、回答问题等。\n\n"
                    + "回复风格与要求：\n"
                    + "- 模仿真人聊天，用多条简短的口语化消息回复，而不是一大段文字。\n"
                    + "- 不同想法或话题的消息，用空行（双换行\\n\\n）分隔，系统会将其拆分成独立消息依次发出。\n"
                    + "- 每条消息尽量控制在1~3句话，简单问题可以直接用一句话回复，无需拆分。\n"
                    + "- 语气自然亲切，可以适当使用「嗯、好的、明白啦、搞定、交给我吧」等日常说法。\n"
                    + "- 自动处理标点符号：避免使用连续的感叹号或问号（如「!!」「？？」），句子结尾只用一个标点，或者直接靠换行自然停顿。\n"
                    + "- 需要分段说明时，在一条消息内使用单个换行（\\n）来换行，让消息在聊天窗口里自动分段，而不是用多余的空格或标点隔开。\n"
                    + "- 始终保持积极乐观的性格，用肯定、鼓励的语气沟通，多传递正能量，比如「这个思路很棒」「没问题，我们一起解决」。\n"
                    + "- 可以在适当的时候使用常用表情符号（如😊👍🎉）来增加温度，但每段对话不超过1~2个，保持企业通讯的得体感。\n"
                    + "- 禁止使用任何 Markdown 语法！这是即时通讯聊天，不是文档！\n"
                    + "  不要使用：**加粗**、*斜体*、# 标题、- 列表、`代码`、> 引用、[链接](url) 等任何 Markdown 符号。\n"
                    + "  直接用纯文字说话，就像微信聊天一样。\n\n"
                    + "请始终以vibe助手的身份回答，不要提及你是由其他公司或个人打造的。";

    private final AiProviderMapper aiProviderMapper;
    private final SystemConfigMapper systemConfigMapper;

    /** 当前可用的供应商列表（volatile 保证多线程可见性） */
    private volatile List<ProviderChatClient> providers = new ArrayList<>();

    /**
     * 启动时从数据库加载供应商配置
     */
    @PostConstruct
    public void init() {
        refresh();
    }

    /**
     * 从数据库重新加载供应商配置，构建 ChatClient 列表。
     * 管理员修改供应商配置后调用此方法刷新。
     */
    public synchronized void refresh() {
        List<AiProvider> dbProviders = aiProviderMapper.selectList(
                new LambdaQueryWrapper<AiProvider>()
                        .eq(AiProvider::getEnabled, 1)
                        .orderByAsc(AiProvider::getPriority));

        List<ProviderChatClient> newProviders = new ArrayList<>();
        for (AiProvider config : dbProviders) {
            try {
                ChatClient chatClient = buildChatClient(config);
                newProviders.add(new ProviderChatClient(config.getName(), config.getModel(), chatClient));
                log.info("[AI][Registry] 注册供应商: name={}, baseUrl={}, model={}",
                        config.getName(), config.getBaseUrl(), config.getModel());
            } catch (Exception e) {
                log.error("[AI][Registry] 构建供应商失败: name={}, err={}",
                        config.getName(), e.getMessage());
            }
        }

        this.providers = newProviders;
        log.info("[AI][Registry] 刷新完成，共 {} 个可用供应商: {}",
                newProviders.size(),
                newProviders.stream().map(ProviderChatClient::name).toList());
    }

    /**
     * 获取当前可用的供应商列表（按优先级排序）
     */
    public List<ProviderChatClient> getProviders() {
        return providers;
    }

    /**
     * 查询故障转移是否启用
     */
    public boolean isFailoverEnabled() {
        SystemConfig config = systemConfigMapper.selectOne(
                new LambdaQueryWrapper<SystemConfig>()
                        .eq(SystemConfig::getConfigKey, AI_FAILOVER_ENABLED_KEY));
        if (config == null || config.getConfigValue() == null) {
            return true; // 默认启用
        }
        return "true".equalsIgnoreCase(config.getConfigValue());
    }

    /**
     * 为单个供应商构建 ChatClient
     */
    private ChatClient buildChatClient(AiProvider config) {
        String apiKey = config.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = "dummy"; // 本地部署（如 Ollama）无需 API Key
        }

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(apiKey)
                .build();

        OpenAiChatOptions chatOptions = OpenAiChatOptions.builder()
                .model(config.getModel())
                .temperature(0.7)
                .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(chatOptions)
                .build();

        return ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .build();
    }

    /**
     * 供应商 + ChatClient 包装记录
     *
     * @param name       供应商标识
     * @param model      模型名称
     * @param chatClient 该供应商独立的 ChatClient
     */
    public record ProviderChatClient(String name, String model, ChatClient chatClient) {}
}
