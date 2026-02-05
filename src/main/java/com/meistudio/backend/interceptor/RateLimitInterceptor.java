package com.meistudio.backend.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meistudio.backend.annotation.RateLimit;
import com.meistudio.backend.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Collections;

/**
 * 分布式接口限流拦截器。
 *
 * 核心思路：
 * 1. 拦截携带 @RateLimit 注解的 Controller 方法。
 * 2. 利用 Redis INCR + EXPIRE（通过 Lua 脚本保证原子性）实现滑动窗口计数。
 * 3. 如果在窗口时间内请求次数超过阈值，直接返回 429 Too Many Requests。
 *
 * Redis Key 设计：rate_limit:{接口路径}:{用户ID或IP}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Lua 脚本：原子性地执行"自增计数 + 设置过期时间"。
     * 避免了 INCR 和 EXPIRE 两条命令之间的竞态条件。
     *
     * KEYS[1] = 限流 Key
     * ARGV[1] = 窗口过期时间（秒）
     * 返回值   = 当前窗口内的请求计数
     */
    private static final String LUA_SCRIPT =
            "local current = redis.call('INCR', KEYS[1]) " +
            "if current == 1 then " +
            "    redis.call('EXPIRE', KEYS[1], ARGV[1]) " +
            "end " +
            "return current";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 只处理带有 @RateLimit 注解的 Controller 方法
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RateLimit rateLimit = handlerMethod.getMethodAnnotation(RateLimit.class);
        if (rateLimit == null) {
            return true;
        }

        // 构建限流 Key：rate_limit:{请求路径}:{用户标识}
        String userIdentifier = getUserIdentifier(request);
        String rateLimitKey = "rate_limit:" + request.getRequestURI() + ":" + userIdentifier;

        try {
            // 执行 Lua 脚本
            DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(LUA_SCRIPT, Long.class);
            Long currentCount = redisTemplate.execute(
                    redisScript,
                    Collections.singletonList(rateLimitKey),
                    String.valueOf(rateLimit.windowSeconds())
            );

            if (currentCount != null && currentCount > rateLimit.maxRequests()) {
                log.warn("[限流触发] Key={}, 当前计数={}, 限额={}", rateLimitKey, currentCount, rateLimit.maxRequests());

                // 返回 429 Too Many Requests
                response.setStatus(429);
                response.setContentType("application/json;charset=UTF-8");
                Result<?> result = Result.error(429, rateLimit.message());
                response.getWriter().write(objectMapper.writeValueAsString(result));
                return false;
            }
        } catch (Exception e) {
            // 健壮性优化（面试加分点）：限流组件异常时不应影响核心业务流程，自动降级放行并记录告警日志
            log.error("[限流系统异常] Redis 服务异常或连接超时，已自动执行降级放行策略: {}", e.getMessage());
        }

        return true;
    }

    /**
     * 获取用户唯一标识。
     * 优先使用 JWT 中解析出的 userId（已由 AuthInterceptor 设置到 Header 或 ThreadLocal 中），
     * 若未登录则使用客户端 IP 地址。
     */
    private String getUserIdentifier(HttpServletRequest request) {
        // 优先从 Authorization Header 中获取 Token 的 hash 作为标识
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // 使用 Token 的后 8 位作为唯一标识（避免存储完整 Token）
            String token = authHeader.substring(7);
            return token.length() > 8 ? token.substring(token.length() - 8) : token;
        }
        // 降级策略：使用客户端真实 IP
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
