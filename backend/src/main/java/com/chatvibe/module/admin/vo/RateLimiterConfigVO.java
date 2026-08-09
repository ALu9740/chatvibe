package com.chatvibe.module.admin.vo;

import lombok.Data;

/**
 * 限流器配置视图对象
 *
 * @author Alu
 * @date 2026-08-09
 */
@Data
public class RateLimiterConfigVO {

    /**
     * 限流器名称
     */
    private String name;

    /**
     * 单个周期内可用的许可数
     */
    private Integer limitForPeriod;

    /**
     * 许可刷新周期(如 "1s", "1m", "1h")
     */
    private String limitRefreshPeriod;

    /**
     * 等待许可的超时时间(如 "1s", "1m", "1h")
     */
    private String timeoutDuration;
}
