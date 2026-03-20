package com.meistudio.backend.mcp;

import com.meistudio.backend.entity.Document;
import com.meistudio.backend.mapper.DocumentMapper;
import com.meistudio.backend.mcp.McpProtocol.*;
import com.meistudio.backend.service.tool.WebSearchTool;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * MCP Server 核心引擎 —— 工具与资源的注册中心。
 */
@Component
public class McpServerCore {
    private static final Logger log = LoggerFactory.getLogger(McpServerCore.class);

    private final WebSearchTool webSearchTool;
    private final DocumentMapper documentMapper;
    private final ObjectMapper objectMapper;

    private static final String PROTOCOL_VERSION = "2024-11-05";

    public McpServerCore(WebSearchTool webSearchTool, DocumentMapper documentMapper, ObjectMapper objectMapper) {
        this.webSearchTool = webSearchTool;
        this.documentMapper = documentMapper;
        this.objectMapper = objectMapper;
    }

    // ===================== 协议握手 =====================

    public InitializeResult handleInitialize() {
        log.info("[MCP] 收到 initialize 握手请求");
        
        InitializeResult result = new InitializeResult();
        result.setProtocolVersion(PROTOCOL_VERSION);
        
        ServerCapabilities capabilities = new ServerCapabilities();
        capabilities.setTools(new ToolsCapability());
        capabilities.setResources(new ResourcesCapability());
        result.setCapabilities(capabilities);
        
        result.setServerInfo(new ServerInfo("meistudio-mcp-server", "1.0.0"));
        return result;
    }

    // ===================== Tool 管理 =====================

    public ToolListResult listTools() {
        log.info("[MCP] 列出所有可用 Tool");

        ToolDefinition searchTool = new ToolDefinition();
        searchTool.setName("web_search");
        searchTool.setDescription("根据关键词搜索互联网获取实时信息。当需要查询最新新闻、天气、股价、政策法规等实时数据时使用。");
        searchTool.setInputSchema(Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of(
                                "type", "string",
                                "description", "搜索关键词"
                        )
                ),
                "required", List.of("query")
        ));

        ToolDefinition knowledgeSearchTool = new ToolDefinition();
        knowledgeSearchTool.setName("knowledge_search");
        knowledgeSearchTool.setDescription("在用户的私有知识库中进行语义检索。当用户询问与其上传文档相关的问题时使用。需要提供 userId 参数。");
        knowledgeSearchTool.setInputSchema(Map.of(
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
        ));

        ToolListResult result = new ToolListResult();
        result.setTools(List.of(searchTool, knowledgeSearchTool));
        return result;
    }

    @SuppressWarnings("unchecked")
    public ToolCallResult callTool(String toolName, Map<String, Object> arguments) {
        log.info("[MCP] 调用 Tool: name={}, args={}", toolName, arguments);
        long startTime = System.currentTimeMillis();

        try {
            String resultText = switch (toolName) {
                case "web_search" -> {
                    String query = (String) arguments.get("query");
                    yield webSearchTool.searchWeb(query);
                }
                case "knowledge_search" -> {
                    String query = (String) arguments.get("query");
                    yield "知识库检索结果（query=" + query + "）: 此功能需要用户认证后使用。";
                }
                default -> throw new IllegalArgumentException("未知的 Tool: " + toolName);
            };

            long costMs = System.currentTimeMillis() - startTime;
            log.info("[MCP] Tool 执行完成: name={}, 耗时={}ms", toolName, costMs);

            ToolCallResult callResult = new ToolCallResult();
            callResult.setContent(List.of(ContentBlock.text(resultText)));
            callResult.setError(false);
            return callResult;

        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - startTime;
            log.error("[MCP] Tool 执行失败: name={}, 耗时={}ms, 错误={}", toolName, costMs, e.getMessage());

            ToolCallResult callResult = new ToolCallResult();
            callResult.setContent(List.of(ContentBlock.text("Tool 执行失败: " + e.getMessage())));
            callResult.setError(true);
            return callResult;
        }
    }

    // ===================== Resource 管理 =====================

    public ResourceListResult listResources() {
        log.info("[MCP] 列出所有可用 Resource");

        ResourceDefinition docStats = new ResourceDefinition();
        docStats.setUri("meistudio://knowledge-base/stats");
        docStats.setName("知识库统计信息");
        docStats.setDescription("获取系统中所有文档的处理状态统计（总数、处理中、已完成、失败）");
        docStats.setMimeType("application/json");

        ResourceDefinition recentDocs = new ResourceDefinition();
        recentDocs.setUri("meistudio://knowledge-base/recent");
        recentDocs.setName("最近上传的文档");
        recentDocs.setDescription("获取最近上传的 10 个文档的基本信息");
        recentDocs.setMimeType("application/json");

        ResourceListResult result = new ResourceListResult();
        result.setResources(List.of(docStats, recentDocs));
        return result;
    }

    public ResourceReadResult readResource(String uri) {
        log.info("[MCP] 读取 Resource: uri={}", uri);

        try {
            String content = switch (uri) {
                case "meistudio://knowledge-base/stats" -> getKnowledgeBaseStats();
                case "meistudio://knowledge-base/recent" -> getRecentDocuments();
                default -> throw new IllegalArgumentException("未知的 Resource URI: " + uri);
            };

            ResourceReadResult result = new ResourceReadResult();
            result.setContents(List.of(new ResourceContent(uri, "application/json", content)));
            return result;

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
