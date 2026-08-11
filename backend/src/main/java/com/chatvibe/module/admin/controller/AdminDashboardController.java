package com.chatvibe.module.admin.controller;

import com.chatvibe.common.result.Result;
import com.chatvibe.module.admin.service.AdminDashboardService;
import com.chatvibe.module.admin.vo.AdminUserVO;
import com.chatvibe.module.admin.vo.AiUsageTrendVO;
import com.chatvibe.module.admin.vo.DashboardMetricsVO;
import com.chatvibe.module.admin.vo.MessageTrendVO;
import com.chatvibe.module.admin.vo.SystemHealthVO;
import com.chatvibe.module.admin.vo.UserGrowthTrendVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台仪表盘接口
 *
 * @author Alu
 * @date 2026-08-11
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    /**
     * 获取仪表盘核心指标
     */
    @GetMapping("/dashboard/metrics")
    public Result<DashboardMetricsVO> getMetrics() {
        return Result.success(adminDashboardService.getMetrics());
    }

    /**
     * 获取用户增长趋势
     *
     * @param days 统计天数(默认 30)
     */
    @GetMapping("/dashboard/user-growth")
    public Result<UserGrowthTrendVO> getUserGrowthTrend(
            @RequestParam(defaultValue = "30") Integer days) {
        return Result.success(adminDashboardService.getUserGrowthTrend(days));
    }

    /**
     * 获取消息趋势
     *
     * @param days 统计天数(默认 30)
     */
    @GetMapping("/dashboard/message-trend")
    public Result<MessageTrendVO> getMessageTrend(
            @RequestParam(defaultValue = "30") Integer days) {
        return Result.success(adminDashboardService.getMessageTrend(days));
    }

    /**
     * 获取 AI 使用趋势
     *
     * @param days 统计天数(默认 30)
     */
    @GetMapping("/dashboard/ai-usage")
    public Result<AiUsageTrendVO> getAiUsageTrend(
            @RequestParam(defaultValue = "30") Integer days) {
        return Result.success(adminDashboardService.getAiUsageTrend(days));
    }

    /**
     * 获取系统健康状态
     */
    @GetMapping("/dashboard/health")
    public Result<SystemHealthVO> getSystemHealth() {
        return Result.success(adminDashboardService.getSystemHealth());
    }

    /**
     * 获取当前登录管理员信息
     */
    @GetMapping("/me")
    public Result<AdminUserVO> getAdminInfo() {
        return Result.success(adminDashboardService.getAdminInfo());
    }
}
