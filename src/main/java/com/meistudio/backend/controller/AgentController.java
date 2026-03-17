package com.meistudio.backend.controller;

import com.meistudio.backend.annotation.RateLimit;
import com.meistudio.backend.common.Result;
import com.meistudio.backend.common.UserContext;
import com.meistudio.backend.service.AgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Agent 对话控制器。
 *
 * 提供联网搜索 Agent 的 RESTful 接口，供鸿蒙客户端调用。
 * 用户发送自然语言消息，后端 Agent 自主判断是否需要调用联网搜索工具，
 * 最终返回整合了实时信息的智能回答。
 *
 * 安全机制：
 * - JWT 鉴权：由 AuthInterceptor 统一拦截（/api/** 路径）
 * - 接口限流：@RateLimit 注解限制单用户每分钟请求次数，防止恶意刷量消耗大模型 Token
 * - 审计日志：由 LogAspect 自动记录每次请求的参数、耗时和结果
 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    /**
     * Agent 对话接口。
     *
     * POST /api/agent/chat
     * Body: { "message": "今天北京天气怎么样？" }
     *
     * 响应: { "code": 200, "msg": "success", "data": { "reply": "..." } }
     *
     * @param body 请求体，必须包含 "message" 字段
     * @return Agent 的回答
     */
    @PostMapping("/chat")
    @RateLimit(maxRequests = 10, windowSeconds = 60, message = "Agent 对话请求过于频繁，请稍后再试")
    public Result<Map<String, Object>> chat(@RequestBody Map<String, String> body) {
        String message = body.get("message");

        if (message == null || message.isBlank()) {
            return Result.error(400, "消息内容不能为空");
        }

        Long userId = UserContext.getUserId();
        String reply = agentService.chat(userId, message);

        return Result.success(Map.of("reply", reply));
    }

    /**
     * 清除 Agent 对话记忆（重置上下文）。
     *
     * POST /api/agent/clear
     */
    @PostMapping("/clear")
    public Result<Void> clearMemory() {
        Long userId = UserContext.getUserId();
        agentService.clearMemory(userId);
        return Result.success();
    }
}
