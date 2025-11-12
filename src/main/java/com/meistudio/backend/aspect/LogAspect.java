package com.meistudio.backend.aspect;

import com.meistudio.backend.common.UserContext;
import com.meistudio.backend.util.MaskingUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 全局操作审计日志切面。
 *
 * 基于 Spring AOP 实现，自动拦截所有 Controller 层方法的调用，记录：
 * 1. 操作者身份（userId）
 * 2. 调用的接口方法名
 * 3. 请求参数（对敏感字段自动脱敏）
 * 4. 接口耗时（毫秒）
 * 5. 执行结果或异常信息
 *
 * 不影响业务逻辑，纯旁路记录，满足审计合规需求。
 */
@Slf4j
@Aspect
@Component
public class LogAspect {

    /**
     * 切入点：拦截 controller 包下所有类的所有 public 方法。
     */
    @Pointcut("execution(* com.meistudio.backend.controller..*.*(..))")
    public void controllerPointcut() {
    }

    /**
     * 环绕通知：记录请求参数、耗时和响应结果。
     */
    @Around("controllerPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取当前操作者 ID
        Long userId = null;
        try {
            userId = UserContext.getUserId();
        } catch (Exception ignored) {
            // 登录接口可能还没有设置 userId
        }

        String methodName = joinPoint.getSignature().toShortString();
        String maskedArgs = maskArguments(joinPoint.getArgs());

        log.info("[审计日志] 用户={}, 接口={}, 参数={}", userId, methodName, maskedArgs);

        long startTime = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long costMs = System.currentTimeMillis() - startTime;
            log.info("[审计日志] 用户={}, 接口={}, 耗时={}ms, 结果=成功", userId, methodName, costMs);
            return result;
        } catch (Throwable e) {
            long costMs = System.currentTimeMillis() - startTime;
            log.error("[审计日志] 用户={}, 接口={}, 耗时={}ms, 异常={}", userId, methodName, costMs, e.getMessage());
            throw e;
        }
    }

    /**
     * 对请求参数进行脱敏处理。
     * 自动识别参数中的 apiKey 字段并进行掩码。
     */
    private String maskArguments(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");

            Object arg = args[i];
            if (arg instanceof MultipartFile file) {
                // 文件类型只记录文件名，不记录内容
                sb.append("File(").append(file.getOriginalFilename()).append(")");
            } else if (arg instanceof String str) {
                // 字符串类型检查是否像 API Key
                if (str.startsWith("sk-") || str.length() > 30) {
                    sb.append(MaskingUtil.maskApiKey(str));
                } else {
                    sb.append(str);
                }
            } else if (arg instanceof Map<?, ?> map) {
                // Map 类型需要深度脱敏 apiKey 字段
                sb.append(maskMap(map));
            } else {
                sb.append(arg);
            }
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * 对 Map 参数中的敏感 Key 进行脱敏。
     */
    private String maskMap(Map<?, ?> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) sb.append(", ");
            first = false;

            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();

            if ("apiKey".equalsIgnoreCase(key) || "api_key".equalsIgnoreCase(key)) {
                sb.append(key).append("=").append(MaskingUtil.maskApiKey(String.valueOf(value)));
            } else if ("token".equalsIgnoreCase(key)) {
                sb.append(key).append("=").append(MaskingUtil.maskToken(String.valueOf(value)));
            } else {
                sb.append(key).append("=").append(value);
            }
        }
        sb.append("}");
        return sb.toString();
    }
}
