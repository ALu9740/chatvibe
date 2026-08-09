package com.chatvibe.module.admin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.chatvibe.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统配置实体 (管理员后台动态管理系统参数)
 *
 * @author Alu
 * @date 2026-08-09
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("system_config")
public class SystemConfig extends BaseEntity {

    /**
     * 配置键
     */
    private String configKey;

    /**
     * 配置值(JSON)
     */
    private String configValue;

    /**
     * 配置描述
     */
    private String description;

    /**
     * 最后更新者邮箱
     */
    private String updatedBy;
}
