package com.chatvibe.module.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chatvibe.module.notification.entity.Notification;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消息通知 Mapper
 *
 * @author Alu
 * @date 2026-07-02
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
}
