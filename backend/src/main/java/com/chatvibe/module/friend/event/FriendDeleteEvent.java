package com.chatvibe.module.friend.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 好友删除事件（异步发送通知）
 *
 * @author Alu
 * @date 2026-07-20
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FriendDeleteEvent implements Serializable {
    private Long fromUid;       // 删除发起者
    private Long toUid;         // 被删除方（被通知方）
    private String fromNickname;
}