package com.chatvibe.module.friend.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 好友接受事件（异步发送通知）
 *
 * @author Alu
 * @date 2026-07-20
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FriendAcceptEvent implements Serializable {
    private Long requestId;
    private Long fromUid;   // 请求发起者(被通知方)
    private Long toUid;     // 接受者
    private String acceptNickname;
}