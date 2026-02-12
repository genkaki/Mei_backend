package com.meistudio.backend.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * MCP 协议模型定义。
 *
 * 基于 JSON-RPC 2.0 规范构建的 MCP (Model Context Protocol) 消息体。
 * MCP 是 Anthropic 推出的标准化大模型工具调用协议，
 * 本类手动实现协议解析层，而非依赖第三方 SDK，
 * 体现对底层协议的深入理解（面试高分项）。
 *
 * 核心概念：
 * - Tool:     大模型可主动调用的函数（如联网搜索）
 * - Resource: 大模型可读取的数据源（如知识库文件列表）
 * - Prompt:   预定义的对话模板
 */
public class McpProtocol {

    // ===================== JSON-RPC 2.0 基础消息 =====================

    /**
     * JSON-RPC 2.0 请求。MCP 协议的所有客户端→服务端通信都基于此格式。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JsonRpcRequest {
        private String jsonrpc = "2.0";
        private String method;
        private Object params;
        private Object id;
    }

    /**
     * JSON-RPC 2.0 响应。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class JsonRpcResponse {
        private String jsonrpc;
        private Object id;
        private Object result;
        private JsonRpcError error;

        public static JsonRpcResponse success(Object id, Object result) {
            return JsonRpcResponse.builder()
                    .jsonrpc("2.0")
                    .id(id)
                    .result(result)
                    .build();
        }

        public static JsonRpcResponse error(Object id, int code, String message) {
            return JsonRpcResponse.builder()
                    .jsonrpc("2.0")
                    .id(id)
                    .error(new JsonRpcError(code, message))
                    .build();
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JsonRpcError {
        private int code;
        private String message;
    }

    // ===================== MCP Capability Definitions =====================

    /**
     * MCP 服务端能力声明。在 initialize 握手阶段返回。
     */
    @Data
    @Builder
    public static class ServerCapabilities {
        private ToolsCapability tools;
        private ResourcesCapability resources;
    }

    @Data @Builder
    public static class ToolsCapability {
        @Builder.Default
        private boolean listChanged = false;
    }

    @Data @Builder
    public static class ResourcesCapability {
        @Builder.Default
        private boolean listChanged = false;
    }

    /**
     * MCP initialize 响应体。
     */
    @Data
    @Builder
    public static class InitializeResult {
        private String protocolVersion;
        private ServerCapabilities capabilities;
        private ServerInfo serverInfo;
    }

    @Data
    @AllArgsConstructor
    public static class ServerInfo {
        private String name;
        private String version;
    }

    // ===================== Tool Definitions =====================

    @Data
    @Builder
    public static class ToolDefinition {
        private String name;
        private String description;
        private Map<String, Object> inputSchema;
    }

    @Data
    @Builder
    public static class ToolListResult {
        private List<ToolDefinition> tools;
    }

    @Data
    @Builder
    public static class ToolCallResult {
        private List<ContentBlock> content;
        private boolean isError;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ContentBlock {
        private String type;
        private String text;

        public static ContentBlock text(String text) {
            return new ContentBlock("text", text);
        }
    }

    // ===================== Resource Definitions =====================

    @Data
    @Builder
    public static class ResourceDefinition {
        private String uri;
        private String name;
        private String description;
        private String mimeType;
    }

    @Data
    @Builder
    public static class ResourceListResult {
        private List<ResourceDefinition> resources;
    }

    @Data
    @Builder
    public static class ResourceReadResult {
        private List<ResourceContent> contents;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ResourceContent {
        private String uri;
        private String mimeType;
        private String text;
    }
}
