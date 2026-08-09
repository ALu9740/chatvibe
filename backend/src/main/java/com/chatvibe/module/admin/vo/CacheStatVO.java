package com.chatvibe.module.admin.vo;

import lombok.Data;

/**
 * 缓存统计视图对象
 *
 * @author Alu
 * @date 2026-08-09
 */
@Data
public class CacheStatVO {

    /**
     * 缓存名称
     */
    private String name;

    /**
     * 命中率
     */
    private Double hitRate;

    /**
     * 缓存大小
     */
    private Long size;

    /**
     * 过期时间
     */
    private String ttl;
}
