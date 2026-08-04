package com.chatvibe.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring AI 配置
 * <p>
 * 迁移后由 {@link ChatClient} 统一替代三个手写供应商实现（Qwen/OpenAI/Ollama），
 * 三者均通过 OpenAI 兼容接口调用，仅 base-url/api-key/model 不同（见 application.yml）。
 * <p>
 * 支持两种模式：
 * <ul>
 *   <li>单供应商模式（failover.enabled=false）：使用 Spring AI 自动配置的 ChatModel</li>
 *   <li>多供应商故障转移模式（failover.enabled=true）：为每个供应商手动构建独立 ChatClient，
 *       由 {@code FailoverAiService} 责任链串联</li>
 * </ul>
 *
 * @author Alu
 * @date 2026-07-24
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AiConfig {

    /**
     * 系统提示词：定义 AI 身份为 vibe 助手
     */
    private static final String SYSTEM_PROMPT =
            "你是vibe助手，由Alu打造的一款智能助手。你正在一个即时通讯系统中与用户聊天。\n"
                    + "你可以帮助用户处理工作事务、生成文案、总结内容、回答问题等。\n\n"
                    + "回复风格与要求：\n"
                    + "- 模仿真人聊天，用多条简短的口语化消息回复，而不是一大段文字。\n"
                    + "- 不同想法或话题的消息，用空行（双换行\\n\\n）分隔，系统会将其拆分成独立消息依次发出。\n"
                    + "- 每条消息尽量控制在1~3句话，简单问题可以直接用一句话回复，无需拆分。\n"
                    + "- 语气自然亲切，可以适当使用“嗯、好的、明白啦、搞定、交给我吧”等日常说法。\n"
                    + "- 自动处理标点符号：避免使用连续的感叹号或问号（如“!!”、“？？”），句子结尾只用一个标点，或者直接靠换行自然停顿。\n"
                    + "- 需要分段说明时，在一条消息内使用单个换行（\\n）来换行，让消息在聊天窗口里自动分段，而不是用多余的空格或标点隔开。\n"
                    + "- 始终保持积极乐观的性格，用肯定、鼓励的语气沟通，多传递正能量，比如“这个思路很棒”“没问题，我们一起解决”。\n"
                    + "- 可以在适当的时候使用常用表情符号（如😊👍🎉）来增加温度，但每段对话不超过1~2个，保持企业通讯的得体感。\n"
                    + "- 禁止使用任何 Markdown 语法！这是即时通讯聊天，不是文档！\n"
                    + "  不要使用：**加粗**、*斜体*、# 标题、- 列表、`代码`、> 引用、[链接](url) 等任何 Markdown 符号。\n"
                    + "  直接用纯文字说话，就像微信聊天一样。\n\n"
                    + "请始终以vibe助手的身份回答，不要提及你是由其他公司或个人打造的。";

    @Value("${chatvibe.ai.provider:ollama}")
    private String provider;

    private final AiProviderProperties failoverProperties;

    /**
     * 单供应商模式的 ChatClient（兼容现有 SpringAiChatService）
     * <p>
     * 使用 Spring AI 自动配置注入的 ChatModel（基于 spring.ai.openai.* 配置）。
     * 仅在 failover.enabled=false 时作为 @Primary 注入。
     *
     * @param chatModel Spring AI 自动配置注入的 OpenAI ChatModel
     */
    @Bean
    @Primary
    public ChatClient chatClient(ChatModel chatModel) {
        log.info("[AI] 当前 AI 提供商: {} (Spring AI OpenAI 兼容客户端)", provider);
        return ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .build();
    }

    /**
     * 多供应商 ChatClient 列表（故障转移模式）
     * <p>
     * 为每个 enabled 的供应商手动构建独立的 OpenAiChatModel + ChatClient。
     * 列表顺序与配置文件中的 providers 顺序一致（优先级从高到低）。
     * <p>
     * 当 failover.enabled=false 时返回空列表，不影响单供应商模式。
     *
     * @return 按优先级排序的 ChatClient 列表，每个含供应商标识
     */
    @Bean
    public List<ProviderChatClient> providerChatClients() {
        if (!failoverProperties.isEnabled()) {
            log.info("[AI] 故障转移未启用，使用单供应商模式");
            return List.of();
        }

        List<ProviderChatClient> clients = new ArrayList<>();
        for (AiProviderProperties.ProviderConfig config : failoverProperties.getProviders()) {
            if (!config.isEnabled()) {
                log.info("[AI] 跳过未启用的供应商: {}", config.getName());
                continue;
            }

            // 为每个供应商构建独立的 OpenAiApi → OpenAiChatModel → ChatClient
            OpenAiApi openAiApi = OpenAiApi.builder()
                    .baseUrl(config.getBaseUrl())
                    .apiKey(config.getApiKey())
                    .build();
            OpenAiChatOptions chatOptions = OpenAiChatOptions.builder()
                    .model(config.getModel())
                    .temperature(0.7)
                    .build();
            OpenAiChatModel chatModel = OpenAiChatModel.builder()
                    .openAiApi(openAiApi)
                    .defaultOptions(chatOptions)
                    .build();

            ChatClient chatClient = ChatClient.builder(chatModel)
                    .defaultSystem(SYSTEM_PROMPT)
                    .build();

            clients.add(new ProviderChatClient(config.getName(), chatClient));
            log.info("[AI] 注册供应商: name={}, baseUrl={}, model={}",
                    config.getName(), config.getBaseUrl(), config.getModel());
        }

        log.info("[AI] 故障转移已启用，注册 {} 个供应商: {}",
                clients.size(),
                clients.stream().map(ProviderChatClient::name).toList());
        return clients;
    }

    /**
     * 供应商 + ChatClient 包装记录
     *
     * @param name       供应商标识
     * @param chatClient 该供应商独立的 ChatClient
     */
    public record ProviderChatClient(String name, ChatClient chatClient) {}
}
