package com.chatvibe.module.admin.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 消息趋势
 *
 * @author Alu
 * @date 2026-08-11
 */
@Data
public class MessageTrendVO implements Serializable {

    /**
     * 日期列表(MM-dd)
     */
    private List<String> dates;

    /**
     * 每日消息数
     */
    private List<Long> messages;

    /**
     * 每日 AI 调用次数
     */
    private List<Long> aiCalls;
}
