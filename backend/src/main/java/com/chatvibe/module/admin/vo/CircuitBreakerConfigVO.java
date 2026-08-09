package com.chatvibe.module.admin.vo;

import lombok.Data;

/**
 * 熔断器配置视图对象
 *
 * @author Alu
 * @date 2026-08-09
 */
@Data
public class CircuitBreakerConfigVO {

    /**
     * 熔断器名称
     */
    private String name;

    /**
     * 失败率阈值(百分比)
     */
    private Double failureRateThreshold;

    /**
     * 慢调用率阈值(百分比)
     */
    private Double slowCallRateThreshold;

    /**
     * 慢调用判定时长(如 "2s", "1m")
     */
    private String slowCallDurationThreshold;

    /**
     * 熔断打开后等待时间(如 "10s", "1m")
     */
    private String waitDurationInOpenState;

    /**
     * 半开状态允许的调用数
     */
    private Integer permittedNumberOfCallsInHalfOpenState;
}
