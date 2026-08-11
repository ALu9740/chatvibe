package com.chatvibe.module.admin.service;

import com.chatvibe.module.admin.vo.AdminUserVO;
import com.chatvibe.module.admin.vo.AiUsageTrendVO;
import com.chatvibe.module.admin.vo.DashboardMetricsVO;
import com.chatvibe.module.admin.vo.MessageTrendVO;
import com.chatvibe.module.admin.vo.SystemHealthVO;
import com.chatvibe.module.admin.vo.UserGrowthTrendVO;

/**
 * 管理后台仪表盘服务
 *
 * @author Alu
 * @date 2026-08-11
 */
public interface AdminDashboardService {

    /**
     * 获取仪表盘核心指标
     *
     * @return 核心指标
     */
    DashboardMetricsVO getMetrics();

    /**
     * 获取用户增长趋势
     *
     * @param days 统计天数
     * @return 用户增长趋势
     */
    UserGrowthTrendVO getUserGrowthTrend(int days);

    /**
     * 获取消息趋势
     *
     * @param days 统计天数
     * @return 消息趋势
     */
    MessageTrendVO getMessageTrend(int days);

    /**
     * 获取 AI 使用趋势
     *
     * @param days 统计天数
     * @return AI 使用趋势
     */
    AiUsageTrendVO getAiUsageTrend(int days);

    /**
     * 获取系统健康状态
     *
     * @return 系统健康状态
     */
    SystemHealthVO getSystemHealth();

    /**
     * 获取当前登录管理员信息
     *
     * @return 管理员信息
     */
    AdminUserVO getAdminInfo();
}
