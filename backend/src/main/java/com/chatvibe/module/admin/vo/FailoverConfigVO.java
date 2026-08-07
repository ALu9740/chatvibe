package com.chatvibe.module.admin.vo;

import lombok.Data;

import java.util.List;

/**
 * AI 故障转移配置 VO
 *
 * @author Alu
 * @date 2026-08-06
 */
@Data
public class FailoverConfigVO {

    /**
     * 是否启用故障转移
     */
    private Boolean enabled;

    /**
     * 供应商优先级列表(按优先级排序的供应商名称)
     */
    private List<String> priority;
}
