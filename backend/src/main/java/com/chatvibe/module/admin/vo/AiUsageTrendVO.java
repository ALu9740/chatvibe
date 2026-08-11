package com.chatvibe.module.admin.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * AI 使用趋势
 *
 * @author Alu
 * @date 2026-08-11
 */
@Data
public class AiUsageTrendVO implements Serializable {

    /**
     * 日期列表(MM-dd)
     */
    private List<String> dates;

    /**
     * 每日 AI 调用次数
     */
    private List<Long> calls;

    /**
     * 供应商调用分布
     */
    private List<ProviderBreakdown> providerBreakdown;

    /**
     * 供应商调用分布项
     */
    @Data
    public static class ProviderBreakdown implements Serializable {

        /**
         * 供应商名称
         */
        private String name;

        /**
         * 调用次数
         */
        private Long value;

        public ProviderBreakdown() {
        }

        public ProviderBreakdown(String name, Long value) {
            this.name = name;
            this.value = value;
        }
    }
}
