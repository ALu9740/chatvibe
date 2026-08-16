package com.chatvibe.module.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chatvibe.common.exception.BusinessException;
import com.chatvibe.common.result.PageResult;
import com.chatvibe.common.result.ResultCode;
import com.chatvibe.config.AiProviderRegistry;
import com.chatvibe.module.admin.dto.SaveAiProviderDTO;
import com.chatvibe.module.admin.entity.AiProvider;
import com.chatvibe.module.admin.entity.SystemConfig;
import com.chatvibe.module.admin.enums.OperationTypeEnum;
import com.chatvibe.module.admin.mapper.AiProviderMapper;
import com.chatvibe.module.admin.mapper.SystemConfigMapper;
import com.chatvibe.module.admin.service.AdminAiService;
import com.chatvibe.module.admin.service.AdminLogService;
import com.chatvibe.module.admin.vo.AiConversationMessageVO;
import com.chatvibe.module.admin.vo.AiConversationRecordVO;
import com.chatvibe.module.admin.vo.AiProviderVO;
import com.chatvibe.module.admin.vo.FailoverConfigVO;
import com.chatvibe.module.chat.entity.Conversation;
import com.chatvibe.module.chat.entity.Message;
import com.chatvibe.module.chat.mapper.ConversationMapper;
import com.chatvibe.module.chat.mapper.MessageMapper;
import com.chatvibe.module.user.entity.User;
import com.chatvibe.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理员 AI 服务管理实现
 * <p>
 * 所有供应商配置和故障转移开关均存储在数据库中（ai_provider 表 + system_config 表），
 * 管理员通过后台修改后，调用 {@link AiProviderRegistry#refresh()} 实时生效。
 *
 * @author Alu
 * @date 2026-08-06
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAiServiceImpl implements AdminAiService {

    private static final String AI_FAILOVER_ENABLED_KEY = "ai_failover_enabled";

    private final AiProviderMapper aiProviderMapper;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final SystemConfigMapper systemConfigMapper;
    private final UserMapper userMapper;
    private final AdminLogService adminLogService;
    private final AiProviderRegistry aiProviderRegistry;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public List<AiProviderVO> getProviders() {
        List<AiProvider> providers = aiProviderMapper.selectList(
                new LambdaQueryWrapper<AiProvider>()
                        .orderByAsc(AiProvider::getPriority));
        return providers.stream().map(p -> {
            AiProviderVO vo = new AiProviderVO();
            vo.setId(p.getId());
            vo.setName(p.getName());
            vo.setType(p.getType());
            vo.setStatus(p.getStatus());
            vo.setModel(p.getModel());
            vo.setBaseUrl(p.getBaseUrl());
            vo.setApiKey(maskApiKey(p.getApiKey()));
            vo.setLatency(p.getLatency());
            vo.setPriority(p.getPriority());
            vo.setCreatedAt(p.getCreatedAt() != null ? p.getCreatedAt().format(FORMATTER) : null);
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addProvider(SaveAiProviderDTO dto) {
        Long count = aiProviderMapper.selectCount(
                new LambdaQueryWrapper<AiProvider>().eq(AiProvider::getName, dto.getName()));
        if (count > 0) {
            throw new BusinessException(ResultCode.AI_PROVIDER_NAME_EXISTS);
        }
        AiProvider provider = new AiProvider();
        provider.setName(dto.getName());
        provider.setType(dto.getType());
        provider.setModel(dto.getModel());
        provider.setBaseUrl(dto.getBaseUrl());
        provider.setApiKey(dto.getApiKey());
        provider.setPriority(dto.getPriority());
        provider.setStatus("offline");
        provider.setLatency(0);
        provider.setEnabled(1);
        aiProviderMapper.insert(provider);
        adminLogService.log(OperationTypeEnum.AI_PROVIDER_ADD, "添加AI供应商: " + dto.getName());
        log.info("[管理员] 添加AI供应商: name={}", dto.getName());
        // 刷新供应商注册中心
        aiProviderRegistry.refresh();
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateProvider(Long id, SaveAiProviderDTO dto) {
        AiProvider provider = aiProviderMapper.selectById(id);
        if (provider == null) {
            throw new BusinessException(ResultCode.AI_PROVIDER_NOT_FOUND);
        }
        Long count = aiProviderMapper.selectCount(
                new LambdaQueryWrapper<AiProvider>()
                        .eq(AiProvider::getName, dto.getName())
                        .ne(AiProvider::getId, id));
        if (count > 0) {
            throw new BusinessException(ResultCode.AI_PROVIDER_NAME_EXISTS);
        }
        provider.setName(dto.getName());
        provider.setType(dto.getType());
        provider.setModel(dto.getModel());
        provider.setBaseUrl(dto.getBaseUrl());
        // apiKey 为空时不修改（编辑时留空表示不修改）
        if (dto.getApiKey() != null && !dto.getApiKey().isBlank()) {
            provider.setApiKey(dto.getApiKey());
        }
        provider.setPriority(dto.getPriority());
        aiProviderMapper.updateById(provider);
        adminLogService.log(OperationTypeEnum.AI_PROVIDER_UPDATE, "更新AI供应商: " + dto.getName());
        log.info("[管理员] 更新AI供应商: id={}, name={}", id, dto.getName());
        // 刷新供应商注册中心
        aiProviderRegistry.refresh();
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteProvider(Long id) {
        AiProvider provider = aiProviderMapper.selectById(id);
        if (provider == null) {
            throw new BusinessException(ResultCode.AI_PROVIDER_NOT_FOUND);
        }
        aiProviderMapper.deleteById(id);
        adminLogService.log(OperationTypeEnum.AI_PROVIDER_DELETE, "删除AI供应商: " + provider.getName());
        log.info("[管理员] 删除AI供应商: id={}, name={}", id, provider.getName());
        // 刷新供应商注册中心
        aiProviderRegistry.refresh();
        return true;
    }

    @Override
    public Map<String, Object> testProvider(Long id) {
        AiProvider provider = aiProviderMapper.selectById(id);
        if (provider == null) {
            throw new BusinessException(ResultCode.AI_PROVIDER_NOT_FOUND);
        }
        // 标记为检测中
        provider.setStatus("checking");
        aiProviderMapper.updateById(provider);

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(15000);
        RestTemplate restTemplate = new RestTemplate(factory);

        Map<String, Object> result = new HashMap<>();
        long start = System.currentTimeMillis();
        try {
            // 构建测试用的聊天补全请求（OpenAI 兼容协议）
            // 向 {baseUrl}/v1/chat/completions 发送 POST 请求，携带 API Key 和最小化请求体
            String baseUrl = provider.getBaseUrl();
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            // 避免重复拼接 /v1
            String testUrl;
            if (baseUrl.endsWith("/v1")) {
                testUrl = baseUrl + "/chat/completions";
            } else {
                testUrl = baseUrl + "/v1/chat/completions";
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String apiKey = provider.getApiKey();
            if (apiKey != null && !apiKey.isBlank()) {
                headers.setBearerAuth(apiKey);
            }

            // 最小化请求体：max_tokens=1 降低消耗
            String requestBody = String.format(
                    "{\"model\":\"%s\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"max_tokens\":1}",
                    provider.getModel());

            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    testUrl, HttpMethod.POST, entity, String.class);

            int latency = (int) (System.currentTimeMillis() - start);
            if (response.getStatusCode().is2xxSuccessful()) {
                provider.setStatus("online");
                provider.setLatency(latency);
                aiProviderMapper.updateById(provider);
                result.put("success", true);
                result.put("latency", latency);
                result.put("message", "连接成功");
            } else {
                provider.setStatus("offline");
                provider.setLatency(0);
                aiProviderMapper.updateById(provider);
                result.put("success", false);
                result.put("latency", 0);
                result.put("message", "连接失败: HTTP " + response.getStatusCode().value());
            }
        } catch (Exception e) {
            provider.setStatus("offline");
            provider.setLatency(0);
            aiProviderMapper.updateById(provider);
            result.put("success", false);
            result.put("latency", 0);
            result.put("message", "连接失败: " + e.getMessage());
            log.warn("[管理员] AI供应商连接测试失败: id={}, baseUrl={}, model={}, err={}",
                    id, provider.getBaseUrl(), provider.getModel(), e.getMessage());
        }
        return result;
    }

    @Override
    public FailoverConfigVO getFailoverConfig() {
        FailoverConfigVO vo = new FailoverConfigVO();
        // 从 system_config 表读取故障转移开关
        vo.setEnabled(aiProviderRegistry.isFailoverEnabled());
        // 从 ai_provider 表查询并按优先级排序，返回供应商名称列表
        List<AiProvider> providers = aiProviderMapper.selectList(null);
        List<String> priority = providers.stream()
                .sorted(Comparator.comparingInt(p -> p.getPriority() == null ? Integer.MAX_VALUE : p.getPriority()))
                .map(AiProvider::getName)
                .collect(Collectors.toList());
        vo.setPriority(priority);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateFailoverConfig(FailoverConfigVO config) {
        // 1. 保存故障转移开关到 system_config 表
        SystemConfig failoverConfig = systemConfigMapper.selectOne(
                new LambdaQueryWrapper<SystemConfig>()
                        .eq(SystemConfig::getConfigKey, AI_FAILOVER_ENABLED_KEY));
        String enabledValue = Boolean.TRUE.equals(config.getEnabled()) ? "true" : "false";
        if (failoverConfig == null) {
            failoverConfig = new SystemConfig();
            failoverConfig.setConfigKey(AI_FAILOVER_ENABLED_KEY);
            failoverConfig.setConfigValue(enabledValue);
            failoverConfig.setDescription("AI故障转移开关");
            systemConfigMapper.insert(failoverConfig);
        } else {
            failoverConfig.setConfigValue(enabledValue);
            systemConfigMapper.updateById(failoverConfig);
        }

        // 2. 如果前端传了优先级列表，按列表顺序更新各供应商的 priority
        if (config.getPriority() != null && !config.getPriority().isEmpty()) {
            List<AiProvider> allProviders = aiProviderMapper.selectList(null);
            Map<String, AiProvider> providerMap = allProviders.stream()
                    .collect(Collectors.toMap(AiProvider::getName, Function.identity(), (a, b) -> a));
            for (int i = 0; i < config.getPriority().size(); i++) {
                String name = config.getPriority().get(i);
                AiProvider provider = providerMap.get(name);
                if (provider != null) {
                    provider.setPriority(i + 1);
                    aiProviderMapper.updateById(provider);
                }
            }
        }

        adminLogService.log(OperationTypeEnum.FAILOVER_CONFIG,
                "更新故障转移配置: enabled=" + enabledValue + ", priority=" + config.getPriority());
        log.info("[管理员] 更新故障转移配置: enabled={}, priority={}", enabledValue, config.getPriority());
        // 刷新供应商注册中心，使新配置实时生效
        aiProviderRegistry.refresh();
        return true;
    }

    @Override
    public PageResult<AiConversationRecordVO> getAiConversations(int page, int size, String search, String provider) {
        int safeSize = Math.min(size, 100);

        // 1. 如果提供了用户搜索关键词，先查出匹配的 userId 集合
        Set<Long> matchedUserIds = null;
        if (search != null && !search.trim().isEmpty()) {
            String keyword = search.trim();
            List<User> matchedUsers;
            try {
                Long userId = Long.parseLong(keyword);
                matchedUsers = userMapper.selectList(
                        new LambdaQueryWrapper<User>()
                                .eq(User::getId, userId)
                                .or()
                                .like(User::getNickname, keyword));
            } catch (NumberFormatException e) {
                matchedUsers = userMapper.selectList(
                        new LambdaQueryWrapper<User>()
                                .like(User::getNickname, keyword));
            }
            matchedUserIds = matchedUsers.stream()
                    .map(User::getId)
                    .collect(Collectors.toSet());
            if (matchedUserIds.isEmpty()) {
                return PageResult.of(0L, (long) page, (long) safeSize, Collections.emptyList());
            }
        }

        // 2. 查询 conversation 表中 type=3（AI会话）的记录（支持按供应商筛选 + 按用户搜索）
        Page<Conversation> pageParam = new Page<>(page, safeSize);
        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getType, 3)
                .orderByDesc(Conversation::getLastMessageAt)
                .orderByDesc(Conversation::getId);

        if (provider != null && !provider.trim().isEmpty()) {
            wrapper.eq(Conversation::getAiProvider, provider.trim());
        }
        if (matchedUserIds != null) {
            wrapper.in(Conversation::getOwnerId, matchedUserIds);
        }

        Page<Conversation> result = conversationMapper.selectPage(pageParam, wrapper);

        List<Conversation> records = result.getRecords();
        if (records.isEmpty()) {
            return PageResult.of(result.getTotal(), (long) page, (long) safeSize, Collections.emptyList());
        }

        // 3. 批量查询用户昵称（消除 N+1）
        Set<Long> userIds = records.stream()
                .map(Conversation::getOwnerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : userMapper.selectList(new LambdaQueryWrapper<User>().in(User::getId, userIds))
                        .stream()
                        .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));

        // 4. 批量统计每个会话的消息数（单次 GROUP BY 查询消除 N+1）
        Set<Long> convIds = records.stream()
                .map(Conversation::getId)
                .collect(Collectors.toSet());
        Map<Long, Integer> messageCountMap = new HashMap<>();
        if (!convIds.isEmpty()) {
            List<Map<String, Object>> countRows = messageMapper.selectMaps(
                    new QueryWrapper<Message>()
                            .select("conversation_id", "count(*) as cnt")
                            .in("conversation_id", convIds)
                            .groupBy("conversation_id"));
            for (Map<String, Object> row : countRows) {
                Long convId = ((Number) row.get("conversation_id")).longValue();
                int cnt = ((Number) row.get("cnt")).intValue();
                messageCountMap.put(convId, cnt);
            }
        }

        // 5. 构建 VO（provider/model 来自 conversation 表的 aiProvider/aiModel 字段）
        List<AiConversationRecordVO> voList = records.stream().map(conv -> {
            AiConversationRecordVO vo = new AiConversationRecordVO();
            vo.setId(conv.getId());
            vo.setUserId(conv.getOwnerId());
            User user = userMap.get(conv.getOwnerId());
            vo.setUserNickname(user != null ? user.getNickname() : null);
            vo.setTitle(conv.getName());
            vo.setProvider(conv.getAiProvider());
            vo.setModel(conv.getAiModel());
            vo.setLastMessageAt(conv.getLastMessageAt() != null
                    ? conv.getLastMessageAt().format(FORMATTER)
                    : (conv.getCreatedAt() != null ? conv.getCreatedAt().format(FORMATTER) : null));
            vo.setMessageCount(messageCountMap.getOrDefault(conv.getId(), 0));
            return vo;
        }).collect(Collectors.toList());

        return PageResult.of(result.getTotal(), (long) page, (long) safeSize, voList);
    }

    @Override
    public List<AiConversationMessageVO> getAiConversationMessages(Long conversationId) {
        // 校验会话存在于 conversation 表且为 AI 会话（type=3）
        Conversation conv = conversationMapper.selectById(conversationId);
        if (conv == null || conv.getType() == null || conv.getType() != 3) {
            throw new BusinessException(ResultCode.NOT_FOUND, "AI 对话不存在");
        }

        // 查询消息（最多 200 条，对齐 GET /ai/conversations/{id}/messages 限制）
        List<Message> messages = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, conversationId)
                        .orderByAsc(Message::getId)
                        .last("LIMIT 200"));

        if (messages.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量查询用户信息（仅非 AI 消息的发送者）
        Set<Long> userIds = messages.stream()
                .map(Message::getSenderId)
                .filter(id -> id != null && id != 0L)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : userMapper.selectList(new LambdaQueryWrapper<User>().in(User::getId, userIds))
                        .stream()
                        .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));

        return messages.stream().map(msg -> {
            AiConversationMessageVO vo = new AiConversationMessageVO();
            vo.setId(msg.getId());
            vo.setSenderId(msg.getSenderId());
            vo.setType(msg.getType());
            vo.setContent(msg.getContent());
            vo.setCreatedAt(msg.getCreatedAt() != null ? msg.getCreatedAt().format(FORMATTER) : null);

            if (msg.getSenderId() != null && msg.getSenderId() == 0L) {
                vo.setIsAi(true);
                vo.setSenderName("Vibe助手");
                vo.setSenderAvatar("🤖");
                vo.setProvider(msg.getProvider());
            } else {
                vo.setIsAi(false);
                User user = userMap.get(msg.getSenderId());
                vo.setSenderName(user != null ? user.getNickname() : "未知用户");
                vo.setSenderAvatar(user != null ? user.getAvatar() : null);
            }
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 脱敏API密钥：仅显示前4位和后4位，中间用 **** 代替
     */
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 8) {
            return apiKey;
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}
