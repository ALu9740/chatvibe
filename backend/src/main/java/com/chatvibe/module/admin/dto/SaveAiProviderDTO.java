package com.chatvibe.module.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 新增/更新 AI 供应商 DTO
 *
 * @author Alu
 * @date 2026-08-06
 */
@Data
public class SaveAiProviderDTO {

    /**
     * 供应商标识(如 qwen/openai/ollama)
     */
    @NotBlank(message = "供应商名称不能为空")
    private String name;

    /**
     * 类型: local-本地 cloud-云端
     */
    private String type;

    /**
     * 模型名
     */
    @NotBlank(message = "模型名不能为空")
    private String model;

    /**
     * API base-url
     */
    @NotBlank(message = "baseUrl不能为空")
    private String baseUrl;

    /**
     * API密钥(local 可为空)
     */
    private String apiKey;

    /**
     * 故障转移优先级(数字越小优先级越高)
     */
    @NotNull(message = "优先级不能为空")
    private Integer priority;
}
