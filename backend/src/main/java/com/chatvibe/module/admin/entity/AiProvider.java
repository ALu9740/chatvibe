package com.chatvibe.module.admin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.chatvibe.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 供应商配置实体
 *
 * @author Alu
 * @date 2026-08-06
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_provider")
public class AiProvider extends BaseEntity {

    /**
     * 供应商标识(如 qwen/openai/ollama)
     */
    private String name;

    /**
     * 类型: local-本地 cloud-云端
     */
    private String type;

    /**
     * 模型名
     */
    private String model;

    /**
     * API base-url
     */
    private String baseUrl;

    /**
     * API密钥(local 可为空)
     */
    private String apiKey;

    /**
     * 状态: online/offline/checking
     */
    private String status;

    /**
     * 最近一次测试延迟(ms)
     */
    private Integer latency;

    /**
     * 故障转移优先级(数字越小优先级越高)
     */
    private Integer priority;

    /**
     * 是否启用: 0-禁用 1-启用
     */
    private Integer enabled;
}
