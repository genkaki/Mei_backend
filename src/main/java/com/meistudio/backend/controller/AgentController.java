package com.meistudio.backend.controller;

import com.meistudio.backend.annotation.RateLimit;
import com.meistudio.backend.common.Result;
import com.meistudio.backend.common.UserContext;
import com.meistudio.backend.service.AgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
@Slf4j
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

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
    public Result<Map<String, Object>> chat(@RequestBody Map<String, Object> body) {
        String message = (String) body.get("message");
        AgentService.AgentConfig config = parseConfig(body);

        if (message == null || message.isBlank()) {
            return Result.error(400, "消息内容不能为空");
        }

        Long userId = UserContext.getUserId();
        String reply = agentService.chat(userId, message, config);

        return Result.success(Map.of("reply", reply));
    }

    /**
     * Agent 流式对话接口 (SSE)。
     */
    @PostMapping(value = "/chat-stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter chatStream(@RequestBody Map<String, Object> body) {
        String message = (String) body.get("message");
        AgentService.AgentConfig config = parseConfig(body);
        Long userId = UserContext.getUserId();

        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter = 
            new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(60000L);

        if (message == null || message.isBlank()) {
            emitter.completeWithError(new IllegalArgumentException("消息内容不能为空"));
            return emitter;
        }

        try {
            agentService.chatStream(userId, message, config)
                    .onNext(token -> {
                        log.debug("[Agent] 流式输出 Token: {}", token);
                        try {
                            emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                                    .name("message")
                                    .data(token));
                        } catch (Exception e) {
                            log.error("[Agent] SSE 发送 token 失败: {}", e.getMessage());
                            emitter.completeWithError(e);
                        }
                    })
                    .onComplete(response -> {
                        log.info("[Agent] 对话流结束。最终生成结果: {}", (response != null && response.content() != null) ? "存在内容" : "空结果");
                        try {
                            emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                                    .name("complete")
                                    .data("done"));
                            emitter.complete();
                        } catch (Exception e) {
                            emitter.completeWithError(e);
                        }
                    })
                    .onError(e -> {
                        log.error("[Agent] 流式对话过程中发生错误: {}", e.getMessage());
                        try {
                            emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                                    .name("error")
                                    .data(e.getMessage()));
                        } catch (Exception ex) {
                            // ignore
                        }
                        emitter.completeWithError(e);
                    })
                    .start();
        } catch (Exception e) {
            log.error("[Agent] 流式对话初始化失败: {}", e.getMessage());
            try {
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                        .name("error")
                        .data(e.getMessage()));
            } catch (Exception ex) {
                // ignore
            }
            emitter.complete();
        }

        return emitter;
    }

    private AgentService.AgentConfig parseConfig(Map<String, Object> body) {
        List<Long> fileIds = null;
        Object rawFileIds = body.get("fileIds");
        if (rawFileIds instanceof List<?> list) {
            fileIds = list.stream()
                .map(obj -> Long.valueOf(obj.toString()))
                .toList();
        }

        Double temperature = 0.7;
        Object rawTemp = body.get("temperature");
        if (rawTemp instanceof Number num) {
            temperature = num.doubleValue();
        }

        List<Long> mcpServerIds = null;
        Object rawMcpIds = body.get("mcpServerIds");
        if (rawMcpIds instanceof List<?> list) {
            mcpServerIds = list.stream()
                .map(obj -> Long.valueOf(obj.toString()))
                .toList();
        }

        return AgentService.AgentConfig.builder()
                .baseUrl((String) body.get("baseUrl"))
                .apiKey((String) body.get("apiKey"))
                .modelName((String) body.get("modelName"))
                .temperature(temperature)
                .fileIds(fileIds)
                .mcpServerIds(mcpServerIds)
                .build();
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
