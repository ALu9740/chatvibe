package com.chatvibe.module.admin.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 系统健康状态
 *
 * @author Alu
 * @date 2026-08-11
 */
@Data
public class SystemHealthVO implements Serializable {

    /**
     * MySQL 健康状态
     */
    private MiddlewareHealth mysql;

    /**
     * Redis 健康状态
     */
    private MiddlewareHealth redis;

    /**
     * RabbitMQ 健康状态
     */
    private MiddlewareHealth rabbitmq;

    /**
     * MinIO 健康状态
     */
    private MiddlewareHealth minio;

    /**
     * 中间件健康状态
     */
    @Data
    public static class MiddlewareHealth implements Serializable {

        /**
         * 中间件名称
         */
        private String name;

        /**
         * 状态: healthy / warning / error
         */
        private String status;

        /**
         * 状态文案
         */
        private String statusText;

        /**
         * 指标列表
         */
        private List<Metric> metrics;

        public MiddlewareHealth() {
        }

        public MiddlewareHealth(String name, String status, String statusText, List<Metric> metrics) {
            this.name = name;
            this.status = status;
            this.statusText = statusText;
            this.metrics = metrics;
        }
    }

    /**
     * 监控指标
     */
    @Data
    public static class Metric implements Serializable {

        /**
         * 指标标签
         */
        private String label;

        /**
         * 指标值
         */
        private String value;

        /**
         * 是否告警
         */
        private Boolean warning;

        public Metric() {
        }

        public Metric(String label, String value, Boolean warning) {
            this.label = label;
            this.value = value;
            this.warning = warning;
        }
    }
}
