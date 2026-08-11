package com.chatvibe.module.admin.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 仪表盘核心指标
 *
 * @author Alu
 * @date 2026-08-11
 */
@Data
public class DashboardMetricsVO implements Serializable {

    /**
     * 总用户数
     */
    private Long totalUsers;

    /**
     * 在线用户数
     */
    private Long onlineUsers;

    /**
     * 今日新增用户数
     */
    private Long todayNewUsers;

    /**
     * 今日消息数
     */
    private Long todayMessages;

    /**
     * 今日 AI 调用次数
     */
    private Long todayAiCalls;

    /**
     * 活跃群组数
     */
    private Long activeGroups;

    /**
     * API 可用性(0-100 百分比)
     */
    private Double apiAvailability;

    /**
     * 平均响应时间(ms)
     */
    private Double avgResponseTime;
}
