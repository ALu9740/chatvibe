package com.chatvibe.module.admin.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 用户增长趋势
 *
 * @author Alu
 * @date 2026-08-11
 */
@Data
public class UserGrowthTrendVO implements Serializable {

    /**
     * 日期列表(MM-dd)
     */
    private List<String> dates;

    /**
     * 累计用户数
     */
    private List<Long> cumulative;
}
