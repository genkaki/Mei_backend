package com.meistudio.backend.annotation;

import java.lang.annotation.*;

/**
 * 自定义限流注解。
 * 标注在 Controller 方法上，基于 Redis + Lua 脚本实现分布式滑动窗口限流。
 *
 * 示例：@RateLimit(maxRequests = 5, windowSeconds = 60)
 * 表示同一用户在 60 秒内最多允许 5 次调用。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * 窗口时间内允许的最大请求次数
     */
    int maxRequests() default 5;

    /**
     * 滑动窗口时间（秒）
     */
    int windowSeconds() default 60;

    /**
     * 限流提示信息
     */
    String message() default "请求过于频繁，请稍后再试";
}
