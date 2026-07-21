package com.chatvibe.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/**
 * RabbitMQ 配置
 *
 * @author Alu
 * @date 2026-07-14
 */
@Configuration
public class RabbitMQConfig {
    // 登出事件
    public static final String USER_LOGOUT_QUEUE = "user.logout.event";
    public static final String USER_LOGOUT_ROUTING_KEY = "user.logout.#";

    // 密码重置事件
    public static final String USER_PASSWORD_RESET_QUEUE = "user.password.reset.event";
    public static final String USER_PASSWORD_RESET_ROUTING_KEY = "user.password.reset.#";

    // 登录事件相关常量
    public static final String USER_LOGIN_QUEUE = "user.login.event";
    public static final String USER_LOGIN_ROUTING_KEY = "user.login.#";

    // 注册事件相关常量
    public static final String USER_REGISTER_QUEUE = "user.register.event";
    public static final String USER_REGISTER_ROUTING_KEY = "user.register.#";

    // 聊天消息相关常量
    public static final String CHAT_EXCHANGE = "chat.exchange";
    public static final String CHAT_MESSAGE_QUEUE = "chat.message.push";
    public static final String CHAT_MESSAGE_ROUTING_KEY = "chat.message.#";

    // 好友请求事件
    public static final String FRIEND_REQUEST_QUEUE = "friend.request.event";
    public static final String FRIEND_REQUEST_ROUTING_KEY = "friend.request.#";

    // 好友接受事件
    public static final String FRIEND_ACCEPT_QUEUE = "friend.accept.event";
    public static final String FRIEND_ACCEPT_ROUTING_KEY = "friend.accept.#";

    // 好友删除事件
    public static final String FRIEND_DELETE_QUEUE = "friend.delete.event";
    public static final String FRIEND_DELETE_ROUTING_KEY = "friend.delete.#";

    // 群邀请事件
    public static final String GROUP_INVITE_QUEUE = "group.invite.event";
    public static final String GROUP_INVITE_ROUTING_KEY = "group.invite.#";

    /** 群邀请事件队列 */
    @Bean
    public Queue groupInviteQueue() {
        return QueueBuilder.durable(GROUP_INVITE_QUEUE).build();
    }

    @Bean
    public Binding groupInviteBinding() {
        return BindingBuilder.bind(groupInviteQueue())
                .to(chatExchange())
                .with(GROUP_INVITE_ROUTING_KEY);
    }

    /** 好友删除事件队列 */
    @Bean
    public Queue friendDeleteQueue() {
        return QueueBuilder.durable(FRIEND_DELETE_QUEUE).build();
    }

    @Bean
    public Binding friendDeleteBinding() {
        return BindingBuilder.bind(friendDeleteQueue())
                .to(chatExchange())
                .with(FRIEND_DELETE_ROUTING_KEY);
    }

    /** 好友接受事件队列 */
    @Bean
    public Queue friendAcceptQueue() {
        return QueueBuilder.durable(FRIEND_ACCEPT_QUEUE).build();
    }

    @Bean
    public Binding friendAcceptBinding() {
        return BindingBuilder.bind(friendAcceptQueue())
                .to(chatExchange())
                .with(FRIEND_ACCEPT_ROUTING_KEY);
    }

    /** 好友请求事件队列 */
    @Bean
    public Queue friendRequestQueue() {
        return QueueBuilder.durable(FRIEND_REQUEST_QUEUE).build();
    }

    @Bean
    public Binding friendRequestBinding() {
        return BindingBuilder.bind(friendRequestQueue())
                .to(chatExchange())
                .with(FRIEND_REQUEST_ROUTING_KEY);
    }

    /** 登出事件队列 */
    @Bean
    public Queue userLogoutQueue() {
        return QueueBuilder.durable(USER_LOGOUT_QUEUE).build();
    }

    @Bean
    public Binding userLogoutBinding() {
        return BindingBuilder.bind(userLogoutQueue())
                .to(chatExchange())
                .with(USER_LOGOUT_ROUTING_KEY);
    }

    /** 密码重置事件队列 */
    @Bean
    public Queue userPasswordResetQueue() {
        return QueueBuilder.durable(USER_PASSWORD_RESET_QUEUE).build();
    }

    @Bean
    public Binding userPasswordResetBinding() {
        return BindingBuilder.bind(userPasswordResetQueue())
                .to(chatExchange())
                .with(USER_PASSWORD_RESET_ROUTING_KEY);
    }

    /** 注册事件队列 */
    @Bean
    public Queue userRegisterQueue() {
        return QueueBuilder.durable(USER_REGISTER_QUEUE).build();
    }

    /** 绑定到已有的 chatExchange（复用交换机） */
    @Bean
    public Binding userRegisterBinding() {
        return BindingBuilder.bind(userRegisterQueue())
                .to(chatExchange())
                .with(USER_REGISTER_ROUTING_KEY);
    }

    /** 登录事件队列 */
    @Bean
    public Queue userLoginQueue() {
        return QueueBuilder.durable(USER_LOGIN_QUEUE).build();
    }

    /** 绑定到 chatExchange */
    @Bean
    public Binding userLoginBinding() {
        return BindingBuilder.bind(userLoginQueue())
                .to(chatExchange())
                .with(USER_LOGIN_ROUTING_KEY);
    }

    /** Topic 交换机 */
    @Bean
    public TopicExchange chatExchange() {
        return new TopicExchange(CHAT_EXCHANGE, true, false);
    }

    /** 消息推送队列 */
    @Bean
    public Queue chatMessageQueue() {
        return QueueBuilder.durable(CHAT_MESSAGE_QUEUE).build();
    }

    /** 绑定 */
    @Bean
    public Binding chatMessageBinding() {
        return BindingBuilder.bind(chatMessageQueue())
                .to(chatExchange())
                .with(CHAT_MESSAGE_ROUTING_KEY);
    }

    /** JSON 消息转换器 */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /** RabbitTemplate */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}
