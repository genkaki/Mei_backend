package com.meistudio.backend.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meistudio.backend.mcp.McpProtocol.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP SSE 传输层控制器。
 *
 * 实现了 MCP 协议规范中的 HTTP + SSE 传输方式。
 * 这是一套手写的协议传输层实现，体现了对 JSON-RPC 2.0 规范、
 * SSE 长连接机制以及异步消息推送的深入理解。
 *
 * 传输机制说明（面试必考）：
 * 1. 客户端首先请求 GET /mcp/sse，建立 SSE 长连接。
 *    服务端通过此连接向客户端推送消息（单向：Server → Client）。
 *
 * 2. 客户端通过 POST /mcp/messages 发送 JSON-RPC 请求。
 *    服务端处理后，通过步骤 1 建立的 SSE 通道推送响应。
 *
 * 3. 这种 "POST 发请求 + SSE 收响应" 的模式，模拟了全双工通信，
 *    是 WebSocket 的轻量替代方案，且对 HTTP 基础设施更加友好。
 *
 * 会话管理：
 * - 每个 SSE 连接分配一个唯一的 sessionId（UUID）。
 * - 使用 ConcurrentHashMap 管理活跃会话，支持多客户端并发连接。
 * - SSE 断开时自动清理会话，防止内存泄漏。
 */
@Slf4j
@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
public class McpSseController {

    private final McpServerCore mcpServerCore;
    private final ObjectMapper objectMapper;

    /**
     * 活跃 SSE 会话注册表。
     * Key: sessionId (UUID), Value: SseEmitter 实例。
     * 使用 ConcurrentHashMap 保证多线程安全。
     */
    private final Map<String, SseEmitter> sessions = new ConcurrentHashMap<>();

    /**
     * 建立 SSE 长连接。
     *
     * MCP 客户端（如 Claude Desktop、mcp-inspector）在连接时，
     * 首先调用此端点建立 Server-Sent Events 通道。
     * 服务端立即推送一个 "endpoint" 事件，告知客户端后续应该向哪个 URL 发送请求。
     *
     * @return SseEmitter 实例，Spring 会自动维护此长连接
     */
    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connect() {
        String sessionId = UUID.randomUUID().toString();

        // SSE 超时设置为 30 分钟（MCP 会话通常是长期的）
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);

        // 注册会话生命周期回调
        emitter.onCompletion(() -> {
            sessions.remove(sessionId);
            log.info("[MCP-SSE] 会话关闭: sessionId={}", sessionId);
        });
        emitter.onTimeout(() -> {
            sessions.remove(sessionId);
            log.info("[MCP-SSE] 会话超时: sessionId={}", sessionId);
        });
        emitter.onError(e -> {
            sessions.remove(sessionId);
            log.error("[MCP-SSE] 会话异常: sessionId={}, error={}", sessionId, e.getMessage());
        });

        sessions.put(sessionId, emitter);
        log.info("[MCP-SSE] 新会话建立: sessionId={}, 当前活跃会话数={}", sessionId, sessions.size());

        // 按照 MCP 协议，立即推送 endpoint 事件
        try {
            emitter.send(SseEmitter.event()
                    .name("endpoint")
                    .data("/mcp/messages?sessionId=" + sessionId));
        } catch (IOException e) {
            log.error("[MCP-SSE] 推送 endpoint 事件失败: sessionId={}", sessionId, e);
        }

        return emitter;
    }

    /**
     * 接收 MCP 客户端的 JSON-RPC 请求。
     *
     * MCP 客户端通过此端点发送所有的协议请求（initialize、tools/list、tools/call 等）。
     * 服务端处理后，将 JSON-RPC 响应通过对应 sessionId 的 SSE 通道推送回去。
     *
     * @param sessionId SSE 会话 ID
     * @param request   JSON-RPC 2.0 请求体
     */
    @PostMapping(value = "/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
    public void handleMessage(
            @RequestParam String sessionId,
            @RequestBody JsonRpcRequest request) {

        log.info("[MCP-SSE] 收到请求: sessionId={}, method={}, id={}", sessionId, request.getMethod(), request.getId());

        SseEmitter emitter = sessions.get(sessionId);
        if (emitter == null) {
            log.warn("[MCP-SSE] 会话不存在: sessionId={}", sessionId);
            return;
        }

        // 路由 JSON-RPC 方法到对应的 MCP 处理逻辑
        JsonRpcResponse response = routeRequest(request);

        // 通过 SSE 通道推送响应
        try {
            emitter.send(SseEmitter.event()
                    .name("message")
                    .data(objectMapper.writeValueAsString(response)));
            log.info("[MCP-SSE] 响应已推送: sessionId={}, method={}", sessionId, request.getMethod());
        } catch (IOException e) {
            log.error("[MCP-SSE] 推送响应失败: sessionId={}, method={}", sessionId, request.getMethod(), e);
            sessions.remove(sessionId);
        }
    }

    /**
     * JSON-RPC 方法路由器。
     *
     * 根据 MCP 协议规范，将不同的 method 路由到 McpServerCore 的对应处理方法。
     * 支持的方法：
     * - initialize:      协议握手
     * - notifications/initialized: 客户端确认初始化完成（无需响应）
     * - tools/list:      列出可用工具
     * - tools/call:      执行工具
     * - resources/list:  列出可用资源
     * - resources/read:  读取资源内容
     */
    @SuppressWarnings("unchecked")
    private JsonRpcResponse routeRequest(JsonRpcRequest request) {
        try {
            Object result = switch (request.getMethod()) {

                case "initialize" -> mcpServerCore.handleInitialize();

                case "notifications/initialized" -> {
                    log.info("[MCP] 客户端确认初始化完成");
                    yield null; // 通知类消息不需要响应
                }

                case "tools/list" -> mcpServerCore.listTools();

                case "tools/call" -> {
                    Map<String, Object> params = toMap(request.getParams());
                    String toolName = (String) params.get("name");
                    Map<String, Object> arguments = (Map<String, Object>) params.getOrDefault("arguments", Map.of());
                    yield mcpServerCore.callTool(toolName, arguments);
                }

                case "resources/list" -> mcpServerCore.listResources();

                case "resources/read" -> {
                    Map<String, Object> params = toMap(request.getParams());
                    String uri = (String) params.get("uri");
                    yield mcpServerCore.readResource(uri);
                }

                case "ping" -> Map.of(); // MCP 心跳检测

                default -> throw new IllegalArgumentException("不支持的 MCP 方法: " + request.getMethod());
            };

            // notifications 不需要响应
            if (result == null && request.getId() == null) {
                return null;
            }

            return JsonRpcResponse.success(request.getId(), result);

        } catch (Exception e) {
            log.error("[MCP] 请求处理失败: method={}, error={}", request.getMethod(), e.getMessage());
            return JsonRpcResponse.error(request.getId(), -32603, e.getMessage());
        }
    }

    /**
     * 将 JSON-RPC 的 params 字段安全转换为 Map。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object params) {
        if (params instanceof Map) {
            return (Map<String, Object>) params;
        }
        return objectMapper.convertValue(params, Map.class);
    }
}
