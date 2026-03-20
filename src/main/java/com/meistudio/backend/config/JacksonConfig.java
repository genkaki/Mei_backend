package com.meistudio.backend.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigInteger;

/**
 * Jackson 配置类。
 * 
 * 核心功能：
 * 将所有的 Long 和 BigInteger 类型在序列化为 JSON 时转换为 String 类型。
 * 
 * 为什么需要这样做？
 * 因为 JavaScript (以及鸿蒙 ArkTS) 的 Number 类型采用 双精度浮点数 (IEEE 754)，
 * 其最大安全整数为 2^53 - 1 (9007199254740991)。
 * 而 Java 的 Long 是 64 位，最大值为 2^63 - 1。
 * 后端使用的 Snowflake ID (雪花算法) 生成的 ID 通常超过了 JS 的安全范围，
 * 直接以 Number 传递会导致末尾精度丢失（变形成 000），从而导致查询不到数据。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jackson2ObjectMapperBuilderCustomizer() {
        return builder -> {
            // 将 Long 类型序列化为 String
            builder.serializerByType(Long.class, ToStringSerializer.instance);
            builder.serializerByType(Long.TYPE, ToStringSerializer.instance);
            
            // 将 BigInteger 类型序列化为 String
            builder.serializerByType(BigInteger.class, ToStringSerializer.instance);
        };
    }
}
