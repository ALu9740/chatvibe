package com.chatvibe.module.friend.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.chatvibe.module.user.vo.UserVO;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 好友请求视图对象
 *
 * @author Alu
 * @date 2026-06-28
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FriendRequestVO {

    private Long id;
    private Long fromUid;
    private Long toUid;
    private String message;
    private Integer status;
    private LocalDateTime createdAt;
    /**
     * 发起者信息
     */
    private UserVO fromUser;
    /**
     * 接收者信息
     */
    private UserVO toUser;
}
