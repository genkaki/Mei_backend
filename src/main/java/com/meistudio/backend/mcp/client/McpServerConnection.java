package com.meistudio.backend.mcp.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meistudio.backend.mcp.McpProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MCP Server 连接器 (极致兼容版)
 *
 * 特性：
 * 1. 强制 HTTP/1.1（兼容 istio-envoy 网关）
 * 2. 同时接受 JSON 和 SSE 响应格式
 * 3. 支持 Mcp-Session-Id 追踪
 * 4. 工具结果采用约定标记格式，支持图片/视频/文件等富内容
 * 5. 详细的请求/响应日志，便于调试 DashScope 500 等问题
 */
public class McpServerConnection {
    private static final Logger log = LoggerFactory.getLogger(McpServerConnection.class);

    private final String serverUrl;
    private final String serverName;
    private final Map<String, String> customHeaders;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AtomicInteger requestIdCounter = new AtomicInteger(1);

    private boolean connected = false;
    private List<McpProtocol.ToolDefinition> discoveredTools = Collections.emptyList();
    private String sessionId = null;

    public boolean isConnected() { return connected; }
    public List<McpProtocol.ToolDefinition> getDiscoveredTools() { return discoveredTools; }

    public McpServerConnection(String serverName, String serverUrl) {
        this(serverName, serverUrl, Collections.emptyMap());
    }

    public McpServerConnection(String serverName, String serverUrl, Map<String, String> customHeaders) {
        this.serverName = serverName;
        this.serverUrl = serverUrl;
        this.customHeaders = customHeaders != null ? new HashMap<>(customHeaders) : new HashMap<>();
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public int connect() {
        try {
            log.info("[McpClient] 开始握手: name={}, url={}, headers={}", serverName, serverUrl, customHeaders.keySet());

            // 1. initialize
            Map<String, Object> initParams = new HashMap<>();
            // 🎯 使用 DashScope 官方要求的协议版本 (2025-06-18)
            initParams.put("protocolVersion", "2025-06-18");
            initParams.put("clientInfo", Map.of("name", "MeiStudio", "version", "1.2.0"));
            
            // 🎯 精简 capabilities，兼容阿里网关校验
            initParams.put("capabilities", Map.of());
            initParams.put("roots", List.of());

            Object initResult = sendRequest("initialize", initParams);
            log.info("[McpClient] 握手成功: name={}", serverName);

            // 2. notifications/initialized
            sendNotification("notifications/initialized");

            // 3. tools/list
            Object toolsResult = sendRequest("tools/list", Map.of());
            this.discoveredTools = parseToolList(toolsResult);
            this.connected = true;

            log.info("[McpClient] 工具发现完成: name={}, 工具数={}", serverName, discoveredTools.size());
            for (McpProtocol.ToolDefinition tool : discoveredTools) {
                log.info("[McpClient]   ✦ {}: {}", tool.getName(), tool.getDescription());
            }

            return discoveredTools.size();

        } catch (Exception e) {
            log.error("[McpClient] 连接失败: name={}, url={}, error={}", 
                    serverName, serverUrl, e.getMessage());
            this.connected = false;
            throw e; // 🎯 核心修复：向上抛出异常，让后端 controller 和前端能看到具体报错（如 401, 500 等）
        }
    }

    /**
     * 调用 MCP Server 上的某个工具。
     * 返回结果采用约定标记格式，支持富内容块：
     * - 普通文本直接返回
     * - 图片：[MCP_IMAGE:url] 或 [MCP_IMAGE_BASE64:mimeType:data]
     * - 视频：[MCP_VIDEO:url]
     * - 文件：[MCP_FILE:fileName|url]
     * - 链接：[MCP_LINK:title|url]
     */
    public String callTool(String toolName, String arguments) {
        try {
            Map<String, Object> args = arguments != null && !arguments.isBlank()
                    ? objectMapper.readValue(arguments, new TypeReference<>() {})
                    : Map.of();

            Map<String, Object> params = Map.of("name", toolName, "arguments", args);
            Object result = sendRequest("tools/call", params);
            return extractRichContent(result);

        } catch (Exception e) {
            log.error("[McpClient] 工具调用失败: server={}, tool={}, error={}", serverName, toolName, e.getMessage());
            return "MCP 工具调用失败: " + e.getMessage();
        }
    }

    public void disconnect() {
        this.connected = false;
        this.discoveredTools = Collections.emptyList();
        log.info("[McpClient] 已断开: name={}", serverName);
    }

    // ==================== HTTP 通讯 ====================

    private HttpRequest.Builder buildBaseRequest() {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) MeiStudio/1.2.0")
                .header("X-DashScope-WorkSpace", "default");

        String apiKey = extractApiKeyFromHeaders();
        if (apiKey != null && !apiKey.isEmpty()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }

        for (Map.Entry<String, String> entry : customHeaders.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty() && !entry.getKey().equalsIgnoreCase("Authorization")) {
                builder.header(entry.getKey(), entry.getValue());
            }
        }

        if (sessionId != null) {
            builder.header("Mcp-Session-Id", sessionId);
        }

        return builder;
    }

    private String extractApiKeyFromHeaders() {
        String auth = customHeaders.getOrDefault("Authorization", "");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        return null;
    }

    private Object sendRequest(String method, Object params) throws Exception {
        int id = requestIdCounter.getAndIncrement();

        McpProtocol.JsonRpcRequest rpcRequest = new McpProtocol.JsonRpcRequest();
        rpcRequest.setJsonrpc("2.0");
        rpcRequest.setMethod(method);
        rpcRequest.setParams(params);
        rpcRequest.setId(id);

        String body = objectMapper.writeValueAsString(rpcRequest);
        log.info("[McpClient] ==> {} (id={}): {}", method, id, body);

        HttpRequest httpRequest = buildBaseRequest()
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        httpResponse.headers().firstValue("Mcp-Session-Id").ifPresent(sid -> {
            this.sessionId = sid;
        });

        int status = httpResponse.statusCode();
        String contentType = httpResponse.headers().firstValue("Content-Type").orElse("");
        String respBody = httpResponse.body();

        log.info("[McpClient] <== {} (id={}): status={}, type={}, len={}",
                method, id, status, contentType, respBody.length());

        if (status != 200 && status != 202) {
            log.error("[McpClient] !!! 异常响应: headers={}", httpResponse.headers().map());
            log.error("[McpClient] !!! 响应体: {}", respBody.isEmpty() ? "(空)" : respBody);
            throw new RuntimeException("MCP 端点响应异常 (" + status + "): " +
                    (respBody.isEmpty() ? "空回复，可能鉴权失败或网关拦截" : respBody));
        }

        if (respBody.isEmpty()) {
            log.warn("[McpClient] !!! 收到 200/202 但响应体为空 (Len=0), 尝试发送初始通知以激活 SSE 流");
            return Map.of("status", "accepted_empty");
        }

        if (contentType.contains("text/event-stream")) {
            return parseSSEResponse(respBody);
        } else {
            return parseJsonResponse(respBody);
        }
    }

    private Object parseJsonResponse(String responseBody) throws Exception {
        McpProtocol.JsonRpcResponse rpcResponse = objectMapper.readValue(
                responseBody, McpProtocol.JsonRpcResponse.class);
        if (rpcResponse.getError() != null) {
            throw new RuntimeException(rpcResponse.getError().getMessage());
        }
        return rpcResponse.getResult();
    }

    private Object parseSSEResponse(String sseBody) throws Exception {
        StringBuilder jsonData = new StringBuilder();
        for (String line : sseBody.split("\n")) {
            line = line.trim();
            if (line.startsWith("data:")) {
                String val = line.substring(5).trim();
                if (!val.equals("[DONE]")) {
                    jsonData.append(val);
                }
            }
        }

        String finalJson = jsonData.toString();
        if (finalJson.isEmpty()) {
            if (sseBody.contains("{") && sseBody.contains("}")) {
                int start = sseBody.indexOf("{");
                int end = sseBody.lastIndexOf("}") + 1;
                return parseJsonResponse(sseBody.substring(start, end));
            }
            throw new RuntimeException("SSE 响应中未找到有效的 JSON 数据");
        }
        return parseJsonResponse(finalJson);
    }

    private void sendNotification(String method) {
        try {
            Map<String, Object> notification = Map.of("jsonrpc", "2.0", "method", method);
            String body = objectMapper.writeValueAsString(notification);
            HttpRequest request = buildBaseRequest()
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            log.warn("[McpClient] 通知发送失败: {}", e.getMessage());
        }
    }

    // ==================== 工具列表解析 ====================

    @SuppressWarnings("unchecked")
    private List<McpProtocol.ToolDefinition> parseToolList(Object result) {
        try {
            String json = objectMapper.writeValueAsString(result);
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});
            List<Map<String, Object>> tools = (List<Map<String, Object>>) map.get("tools");
            if (tools == null) return Collections.emptyList();

            return tools.stream().map(t -> {
                McpProtocol.ToolDefinition def = new McpProtocol.ToolDefinition();
                def.setName((String) t.get("name"));
                def.setDescription((String) t.get("description"));
                def.setInputSchema((Map<String, Object>) t.get("inputSchema"));
                return def;
            }).toList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    // ==================== 富内容提取 ====================

    /**
     * 从 tools/call 的 MCP 结果中提取富内容。
     *
     * MCP 规范定义的 content block 类型：
     *   - text: 纯文本
     *   - image: 图片（含 data base64 或 url）
     *   - resource: 资源（含 uri）
     *
     * 本方法将其转换为约定标记格式，供前端 MarkdownParser 解析：
     *   - [MCP_IMAGE:url]
     *   - [MCP_IMAGE_BASE64:mimeType:data]
     *   - [MCP_VIDEO:url]  (根据 mimeType 判断)
     *   - [MCP_FILE:fileName|url]
     *   - [MCP_LINK:title|url]
     */
    @SuppressWarnings("unchecked")
    private String extractRichContent(Object result) {
        try {
            String json = objectMapper.writeValueAsString(result);
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});
            List<Map<String, Object>> content = (List<Map<String, Object>>) map.get("content");

            if (content == null || content.isEmpty()) return "（工具执行完成，无内容返回）";

            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> block : content) {
                String type = (String) block.get("type");

                if ("text".equals(type)) {
                    sb.append(block.get("text"));

                } else if ("image".equals(type)) {
                    String mimeType = (String) block.getOrDefault("mimeType", "image/png");
                    String data = (String) block.get("data");
                    String url = (String) block.get("url");

                    if (url != null && !url.isEmpty()) {
                        // 🎥 支持视频
                        if (mimeType.startsWith("video/")) {
                            sb.append("\n[MCP_VIDEO:").append(url).append("]\n");
                        } 
                        // 🎵 支持音频
                        else if (mimeType.startsWith("audio/")) {
                            sb.append("\n[MCP_AUDIO:").append(url).append("]\n");
                        }
                        // 🖼️ 支持图片
                        else {
                            sb.append("\n[MCP_IMAGE:").append(url).append("]\n");
                        }
                    } else if (data != null && !data.isEmpty()) {
                        sb.append("\n[MCP_IMAGE_BASE64:").append(mimeType).append(":").append(data).append("]\n");
                    }

                } else if ("resource".equals(type)) {
                    Map<String, Object> resource = (Map<String, Object>) block.get("resource");
                    if (resource != null) {
                        String uri = (String) resource.getOrDefault("uri", "");
                        String resourceName = (String) resource.getOrDefault("name", "资源文件");
                        String resourceMime = (String) resource.getOrDefault("mimeType", "");

                        if (resourceMime.startsWith("video/")) {
                            sb.append("\n[MCP_VIDEO:").append(uri).append("]\n");
                        } else if (resourceMime.startsWith("audio/")) {
                            sb.append("\n[MCP_AUDIO:").append(uri).append("]\n");
                        } else if (resourceMime.startsWith("image/")) {
                            sb.append("\n[MCP_IMAGE:").append(uri).append("]\n");
                        } else {
                            sb.append("\n[MCP_FILE:").append(resourceName).append("|").append(uri).append("]\n");
                        }
                    }
                } else if ("link".equals(type)) {
                    // 🔗 支持链接类型
                    String url = (String) block.get("url");
                    String title = (String) block.getOrDefault("title", "点击打开链接");
                    if (url != null && !url.isEmpty()) {
                        sb.append("\n[MCP_LINK:").append(title).append("|").append(url).append("]\n");
                    }
                }
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("[McpClient] 富内容解析回退为原始文本: {}", e.getMessage());
            return String.valueOf(result);
        }
    }
}
