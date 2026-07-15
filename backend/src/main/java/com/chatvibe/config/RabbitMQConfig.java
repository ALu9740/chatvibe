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
    // 注册事件相关常量
    public static final String USER_REGISTER_QUEUE = "user.register.event";
    public static final String USER_REGISTER_ROUTING_KEY = "user.register.#";

    // 聊天消息相关常量
    public static final String CHAT_EXCHANGE = "chat.exchange";
    public static final String CHAT_MESSAGE_QUEUE = "chat.message.push";
    public static final String CHAT_MESSAGE_ROUTING_KEY = "chat.message.#";

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
