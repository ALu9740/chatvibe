package com.chatvibe;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * ChatVibe 聊天平台后端启动类
 *
 * @author Alu
 * @date 2026-06-27
 */
@SpringBootApplication
@EnableAsync
@MapperScan("com.chatvibe.module.**.mapper")
public class ChatVibeApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatVibeApplication.class, args);
        System.out.println("""
                ====================================================
                  ChatVibe Backend Started Successfully
                ====================================================
                """);
    }
}
