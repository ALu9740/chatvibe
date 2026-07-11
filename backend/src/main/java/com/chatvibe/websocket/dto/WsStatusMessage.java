package com.chatvibe.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WebSocket 用户状态变更广播 DTO
 *
 * @author Alu
 * @date 2026-06-27
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WsStatusMessage {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 在线状态: 0-离线 1-在线 2-忙碌 3-离开
     */
    private Integer status;
}
