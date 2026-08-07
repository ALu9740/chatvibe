package com.chatvibe.module.admin.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * AI 供应商 VO（管理员视图，apiKey 已脱敏）
 *
 * @author Alu
 * @date 2026-08-07
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiProviderVO {

    /**
     * 供应商ID
     */
    private Long id;

    /**
     * 供应商标识
     */
    private String name;

    /**
     * 类型: local-本地 cloud-云端
     */
    private String type;

    /**
     * 状态: online/offline/checking
     */
    private String status;

    /**
     * 模型名
     */
    private String model;

    /**
     * API base-url
     */
    private String baseUrl;

    /**
     * API密钥(已脱敏)
     */
    private String apiKey;

    /**
     * 最近一次测试延迟(ms)
     */
    private Integer latency;

    /**
     * 故障转移优先级(数字越小优先级越高)
     */
    private Integer priority;

    /**
     * 创建时间(格式化字符串)
     */
    private String createdAt;
}
