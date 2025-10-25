package com.meistudio.backend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@MapperScan("com.meistudio.backend.mapper")
@EnableAsync  // 开启异步任务支持，配合 @Async 注解使用
public class MeistudioBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(MeistudioBackendApplication.class, args);
    }
}
