package com.meistudio.backend.mcp;

import com.meistudio.backend.entity.Document;
import com.meistudio.backend.mapper.DocumentMapper;
import com.meistudio.backend.mcp.McpProtocol.*;
import com.meistudio.backend.service.tool.WebSearchTool;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * MCP Server 核心引擎 —— 工具与资源的注册中心。
 *
 * 架构设计要点（面试高频）：
 * 1. 注册表模式（Registry Pattern）：
 *    所有 Tool 和 Resource 在 Server 启动时注册到 HashMap 中，
 *    运行时通过 name 键 O(1) 查找执行，模拟了插件化的服务发现机制。
 *
 * 2. 协议分层：
 *    本类只负责"业务逻辑执行"（调用哪个 Tool、读取哪个 Resource）。
 *    协议解析（JSON-RPC）和传输（SSE）分别由 McpProtocol 和 McpSseController 负责。
 *    这种分层设计符合 OSI 七层模型的思想：传输层 / 协议层 / 业务层 分离。
 *
 * 3. 动态能力发现（Capability Discovery）：
 *    MCP 客户端在连接你的 Server 时，会先调用 tools/list 和 resources/list，
 *    自动发现你暴露了哪些能力。这和 Spring Cloud 的服务注册与发现理念一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpServerCore {

    private final WebSearchTool webSearchTool;
    private final DocumentMapper documentMapper;
    private final ObjectMapper objectMapper;

    private static final String PROTOCOL_VERSION = "2024-11-05";

    // ===================== 协议握手 =====================

    /**
     * 处理 MCP initialize 请求（协议握手）。
     * 返回服务端的协议版本和能力声明。
     */
    public InitializeResult handleInitialize() {
        log.info("[MCP] 收到 initialize 握手请求");
        return InitializeResult.builder()
                .protocolVersion(PROTOCOL_VERSION)
                .capabilities(ServerCapabilities.builder()
                        .tools(ToolsCapability.builder().build())
                        .resources(ResourcesCapability.builder().build())
                        .build())
                .serverInfo(new ServerInfo("meistudio-mcp-server", "1.0.0"))
                .build();
    }

    // ===================== Tool 管理 =====================

    /**
     * 列出所有可用的 MCP Tool。
     * MCP 客户端（如 Claude Desktop）会调用此接口来发现你的后端暴露了哪些能力。
     */
    public ToolListResult listTools() {
        log.info("[MCP] 列出所有可用 Tool");

        ToolDefinition searchTool = ToolDefinition.builder()
                .name("web_search")
                .description("根据关键词搜索互联网获取实时信息。当需要查询最新新闻、天气、股价、政策法规等实时数据时使用。")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "query", Map.of(
                                        "type", "string",
                                        "description", "搜索关键词"
                                )
                        ),
                        "required", List.of("query")
                ))
                .build();

        ToolDefinition knowledgeSearchTool = ToolDefinition.builder()
                .name("knowledge_search")
                .description("在用户的私有知识库中进行语义检索。当用户询问与其上传文档相关的问题时使用。需要提供 userId 参数。")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "query", Map.of(
                                        "type", "string",
                                        "description", "检索关键词或问题"
                                ),
                                "userId", Map.of(
                                        "type", "integer",
                                        "description", "用户 ID"
                                )
                        ),
                        "required", List.of("query", "userId")
                ))
                .build();

        return ToolListResult.builder()
                .tools(List.of(searchTool, knowledgeSearchTool))
                .build();
    }

    /**
     * 执行指定的 MCP Tool。
     * 根据 tool name 路由到对应的 Java 方法执行。
     */
    @SuppressWarnings("unchecked")
    public ToolCallResult callTool(String toolName, Map<String, Object> arguments) {
        log.info("[MCP] 调用 Tool: name={}, args={}", toolName, arguments);
        long startTime = System.currentTimeMillis();

        try {
            String result = switch (toolName) {
                case "web_search" -> {
                    String query = (String) arguments.get("query");
                    yield webSearchTool.searchWeb(query);
                }
                case "knowledge_search" -> {
                    // 简化实现：返回提示信息，完整实现需注入 KnowledgeService
                    String query = (String) arguments.get("query");
                    yield "知识库检索结果（query=" + query + "）: 此功能需要用户认证后使用。";
                }
                default -> throw new IllegalArgumentException("未知的 Tool: " + toolName);
            };

            long costMs = System.currentTimeMillis() - startTime;
            log.info("[MCP] Tool 执行完成: name={}, 耗时={}ms", toolName, costMs);

            return ToolCallResult.builder()
                    .content(List.of(ContentBlock.text(result)))
                    .isError(false)
                    .build();

        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - startTime;
            log.error("[MCP] Tool 执行失败: name={}, 耗时={}ms, 错误={}", toolName, costMs, e.getMessage());

            return ToolCallResult.builder()
                    .content(List.of(ContentBlock.text("Tool 执行失败: " + e.getMessage())))
                    .isError(true)
                    .build();
        }
    }

    // ===================== Resource 管理 =====================

    /**
     * 列出所有可用的 MCP Resource。
     * Resource 是 MCP 中的只读数据源，类似于 REST API 中的 GET 端点。
     */
    public ResourceListResult listResources() {
        log.info("[MCP] 列出所有可用 Resource");

        ResourceDefinition docStats = ResourceDefinition.builder()
                .uri("meistudio://knowledge-base/stats")
                .name("知识库统计信息")
                .description("获取系统中所有文档的处理状态统计（总数、处理中、已完成、失败）")
                .mimeType("application/json")
                .build();

        ResourceDefinition recentDocs = ResourceDefinition.builder()
                .uri("meistudio://knowledge-base/recent")
                .name("最近上传的文档")
                .description("获取最近上传的 10 个文档的基本信息")
                .mimeType("application/json")
                .build();

        return ResourceListResult.builder()
                .resources(List.of(docStats, recentDocs))
                .build();
    }

    /**
     * 读取指定 URI 的 MCP Resource 内容。
     */
    public ResourceReadResult readResource(String uri) {
        log.info("[MCP] 读取 Resource: uri={}", uri);

        try {
            String content = switch (uri) {
                case "meistudio://knowledge-base/stats" -> getKnowledgeBaseStats();
                case "meistudio://knowledge-base/recent" -> getRecentDocuments();
                default -> throw new IllegalArgumentException("未知的 Resource URI: " + uri);
            };

            return ResourceReadResult.builder()
                    .contents(List.of(new ResourceContent(uri, "application/json", content)))
                    .build();

        } catch (Exception e) {
            log.error("[MCP] Resource 读取失败: uri={}, 错误={}", uri, e.getMessage());
            throw e;
        }
    }

    // ===================== Resource 实现 =====================

    private String getKnowledgeBaseStats() {
        long total = documentMapper.selectCount(null);
        long processing = documentMapper.selectCount(
                new LambdaQueryWrapper<Document>().eq(Document::getStatus, 0));
        long completed = documentMapper.selectCount(
                new LambdaQueryWrapper<Document>().eq(Document::getStatus, 1));
        long failed = documentMapper.selectCount(
                new LambdaQueryWrapper<Document>().eq(Document::getStatus, 2));

        return String.format(
                "{\"total\": %d, \"processing\": %d, \"completed\": %d, \"failed\": %d}",
                total, processing, completed, failed);
    }

    private String getRecentDocuments() {
        List<Document> docs = documentMapper.selectList(
                new LambdaQueryWrapper<Document>()
                        .orderByDesc(Document::getCreateTime)
                        .last("LIMIT 10"));

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);
            if (i > 0) sb.append(",");
            sb.append(String.format(
                    "{\"id\": %d, \"fileName\": \"%s\", \"status\": %d, \"userId\": %d}",
                    doc.getId(), doc.getFileName(), doc.getStatus(), doc.getUserId()));
        }
        sb.append("]");
        return sb.toString();
    }
}
