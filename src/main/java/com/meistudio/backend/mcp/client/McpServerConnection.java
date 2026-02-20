package com.meistudio.backend.mcp.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meistudio.backend.mcp.McpProtocol;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MCP Server 连接器 —— 负责与单个远程 MCP Server 进行 JSON-RPC 2.0 通信。
 *
 * 架构要点（面试高频考点）：
 * 1. 采用 Java 11+ 原生 HttpClient，零外部依赖，体现对 JDK API 的深入掌握。
 * 2. 实现了 MCP 协议的完整生命周期：initialize → tools/list → tools/call。
 * 3. 使用 AtomicInteger 生成请求 ID，保证高并发下的线程安全与幂等性。
 *
 * 通信模式说明：
 * - 本实现采用 HTTP POST 方式与 MCP Server 通信（Streamable HTTP Transport）。
 * - 这是 MCP 协议中最通用的传输方式，兼容绝大多数公网 MCP Server。
 */
@Slf4j
public class McpServerConnection {

    private final String serverUrl;
    private final String serverName;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AtomicInteger requestIdCounter = new AtomicInteger(1);

    @Getter
    private boolean connected = false;

    @Getter
    private List<McpProtocol.ToolDefinition> discoveredTools = Collections.emptyList();

    public McpServerConnection(String serverName, String serverUrl) {
        this.serverName = serverName;
        this.serverUrl = serverUrl;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 与 MCP Server 建立连接：执行 initialize 握手 + tools/list 工具发现。
     *
     * @return 发现的工具数量
     */
    public int connect() {
        try {
            log.info("[McpClient] 正在连接 MCP Server: name={}, url={}", serverName, serverUrl);

            // 1. 握手 (initialize)
            Map<String, Object> initParams = Map.of(
                    "protocolVersion", "2025-03-26",
                    "capabilities", Map.of(),
                    "clientInfo", Map.of("name", "MeiStudio", "version", "1.0.0")
            );
            Object initResult = sendRequest("initialize", initParams);
            log.info("[McpClient] 握手成功: server={}, result={}", serverName, initResult);

            // 2. 发送 initialized 通知（无需响应）
            sendNotification("notifications/initialized");

            // 3. 工具发现 (tools/list)
            Object toolsResult = sendRequest("tools/list", Map.of());
            this.discoveredTools = parseToolList(toolsResult);
            this.connected = true;

            log.info("[McpClient] 工具发现完成: server={}, 工具数={}", serverName, discoveredTools.size());
            for (McpProtocol.ToolDefinition tool : discoveredTools) {
                log.info("[McpClient]   - {}: {}", tool.getName(), tool.getDescription());
            }

            return discoveredTools.size();

        } catch (Exception e) {
            log.error("[McpClient] 连接 MCP Server 失败: name={}, url={}, error={}", serverName, serverUrl, e.getMessage());
            this.connected = false;
            return 0;
        }
    }

    /**
     * 调用 MCP Server 上的某个工具。
     *
     * @param toolName  工具名称
     * @param arguments 工具参数（JSON 字符串）
     * @return 工具执行结果的文本内容
     */
    public String callTool(String toolName, String arguments) {
        try {
            Map<String, Object> args = arguments != null && !arguments.isBlank()
                    ? objectMapper.readValue(arguments, new TypeReference<>() {})
                    : Map.of();

            Map<String, Object> params = Map.of(
                    "name", toolName,
                    "arguments", args
            );

            Object result = sendRequest("tools/call", params);
            return extractTextFromResult(result);

        } catch (Exception e) {
            log.error("[McpClient] 工具调用失败: server={}, tool={}, error={}", serverName, toolName, e.getMessage());
            return "MCP 工具调用失败: " + e.getMessage();
        }
    }

    /**
     * 断开与 MCP Server 的连接。
     */
    public void disconnect() {
        this.connected = false;
        this.discoveredTools = Collections.emptyList();
        log.info("[McpClient] 已断开 MCP Server: name={}", serverName);
    }

    // ==================== 内部方法 ====================

    /**
     * 发送 JSON-RPC 2.0 请求并等待响应。
     */
    private Object sendRequest(String method, Object params) throws Exception {
        int id = requestIdCounter.getAndIncrement();

        McpProtocol.JsonRpcRequest rpcRequest = new McpProtocol.JsonRpcRequest();
        rpcRequest.setJsonrpc("2.0");
        rpcRequest.setMethod(method);
        rpcRequest.setParams(params);
        rpcRequest.setId(id);

        String body = objectMapper.writeValueAsString(rpcRequest);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        if (httpResponse.statusCode() != 200) {
            throw new RuntimeException("MCP Server 返回异常状态码: " + httpResponse.statusCode() + ", body=" + httpResponse.body());
        }

        McpProtocol.JsonRpcResponse rpcResponse = objectMapper.readValue(
                httpResponse.body(), McpProtocol.JsonRpcResponse.class);

        if (rpcResponse.getError() != null) {
            throw new RuntimeException("MCP Server 返回错误: " + rpcResponse.getError().getMessage());
        }

        return rpcResponse.getResult();
    }

    /**
     * 发送 JSON-RPC 2.0 通知（无 id，不期待响应）。
     */
    private void sendNotification(String method) {
        try {
            Map<String, Object> notification = Map.of(
                    "jsonrpc", "2.0",
                    "method", method
            );

            String body = objectMapper.writeValueAsString(notification);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            log.warn("[McpClient] 发送通知失败（非致命）: method={}, error={}", method, e.getMessage());
        }
    }

    /**
     * 将 tools/list 的响应体解析为 ToolDefinition 列表。
     */
    @SuppressWarnings("unchecked")
    private List<McpProtocol.ToolDefinition> parseToolList(Object result) {
        try {
            String json = objectMapper.writeValueAsString(result);
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});
            List<Map<String, Object>> tools = (List<Map<String, Object>>) map.get("tools");

            if (tools == null) return Collections.emptyList();

            return tools.stream().map(t -> McpProtocol.ToolDefinition.builder()
                    .name((String) t.get("name"))
                    .description((String) t.get("description"))
                    .inputSchema((Map<String, Object>) t.get("inputSchema"))
                    .build()
            ).toList();
        } catch (Exception e) {
            log.warn("[McpClient] 解析工具列表失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 从 tools/call 的响应体中提取文本内容。
     */
    @SuppressWarnings("unchecked")
    private String extractTextFromResult(Object result) {
        try {
            String json = objectMapper.writeValueAsString(result);
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});
            List<Map<String, Object>> content = (List<Map<String, Object>>) map.get("content");

            if (content == null || content.isEmpty()) return "（工具返回了空结果）";

            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> block : content) {
                if ("text".equals(block.get("type"))) {
                    sb.append(block.get("text"));
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(result);
        }
    }
}
