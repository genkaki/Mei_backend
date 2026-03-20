package com.meistudio.backend.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * MCP 协议模型定义 (手动实现，不依赖 Lombok).
 */
public class McpProtocol {

    // ===================== JSON-RPC 2.0 基础消息 =====================

    public static class JsonRpcRequest {
        private String jsonrpc = "2.0";
        private String method;
        private Object params;
        private Object id;

        public JsonRpcRequest() {}
        public JsonRpcRequest(String method, Object params, Object id) {
            this.method = method;
            this.params = params;
            this.id = id;
        }

        public String getJsonrpc() { return jsonrpc; }
        public void setJsonrpc(String jsonrpc) { this.jsonrpc = jsonrpc; }
        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }
        public Object getParams() { return params; }
        public void setParams(Object params) { this.params = params; }
        public Object getId() { return id; }
        public void setId(Object id) { this.id = id; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class JsonRpcResponse {
        private String jsonrpc = "2.0";
        private Object id;
        private Object result;
        private JsonRpcError error;

        public JsonRpcResponse() {}

        public String getJsonrpc() { return jsonrpc; }
        public void setJsonrpc(String jsonrpc) { this.jsonrpc = jsonrpc; }
        public Object getId() { return id; }
        public void setId(Object id) { this.id = id; }
        public Object getResult() { return result; }
        public void setResult(Object result) { this.result = result; }
        public JsonRpcError getError() { return error; }
        public void setError(JsonRpcError error) { this.error = error; }

        public static JsonRpcResponse success(Object id, Object result) {
            JsonRpcResponse resp = new JsonRpcResponse();
            resp.setId(id);
            resp.setResult(result);
            return resp;
        }

        public static JsonRpcResponse error(Object id, int code, String message) {
            JsonRpcResponse resp = new JsonRpcResponse();
            resp.setId(id);
            resp.setError(new JsonRpcError(code, message));
            return resp;
        }
    }

    public static class JsonRpcError {
        private int code;
        private String message;

        public JsonRpcError() {}
        public JsonRpcError(int code, String message) {
            this.code = code;
            this.message = message;
        }

        public int getCode() { return code; }
        public void setCode(int code) { this.code = code; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    // ===================== Capability Definitions =====================

    public static class ServerCapabilities {
        private ToolsCapability tools;
        private ResourcesCapability resources;

        public ServerCapabilities() {}
        public ToolsCapability getTools() { return tools; }
        public void setTools(ToolsCapability tools) { this.tools = tools; }
        public ResourcesCapability getResources() { return resources; }
        public void setResources(ResourcesCapability resources) { this.resources = resources; }
    }

    public static class ToolsCapability {
        private boolean listChanged = false;
        public ToolsCapability() {}
        public boolean isListChanged() { return listChanged; }
        public void setListChanged(boolean listChanged) { this.listChanged = listChanged; }
    }

    public static class ResourcesCapability {
        private boolean listChanged = false;
        public ResourcesCapability() {}
        public boolean isListChanged() { return listChanged; }
        public void setListChanged(boolean listChanged) { this.listChanged = listChanged; }
    }

    public static class InitializeResult {
        private String protocolVersion;
        private ServerCapabilities capabilities;
        private ServerInfo serverInfo;

        public InitializeResult() {}
        public String getProtocolVersion() { return protocolVersion; }
        public void setProtocolVersion(String protocolVersion) { this.protocolVersion = protocolVersion; }
        public ServerCapabilities getCapabilities() { return capabilities; }
        public void setCapabilities(ServerCapabilities capabilities) { this.capabilities = capabilities; }
        public ServerInfo getServerInfo() { return serverInfo; }
        public void setServerInfo(ServerInfo serverInfo) { this.serverInfo = serverInfo; }
    }

    public static class ServerInfo {
        private String name;
        private String version;

        public ServerInfo() {}
        public ServerInfo(String name, String version) {
            this.name = name;
            this.version = version;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
    }

    // ===================== Tool Definitions =====================

    public static class ToolDefinition {
        private String name;
        private String description;
        private Map<String, Object> inputSchema;

        public ToolDefinition() {}
        public ToolDefinition(String name, String description, Map<String, Object> inputSchema) {
            this.name = name;
            this.description = description;
            this.inputSchema = inputSchema;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Map<String, Object> getInputSchema() { return inputSchema; }
        public void setInputSchema(Map<String, Object> inputSchema) { this.inputSchema = inputSchema; }
    }

    public static class ToolListResult {
        private List<ToolDefinition> tools;
        public ToolListResult() {}
        public List<ToolDefinition> getTools() { return tools; }
        public void setTools(List<ToolDefinition> tools) { this.tools = tools; }
    }

    public static class ToolCallResult {
        private List<ContentBlock> content;
        private boolean isError;
        public ToolCallResult() {}
        public List<ContentBlock> getContent() { return content; }
        public void setContent(List<ContentBlock> content) { this.content = content; }
        public boolean isError() { return isError; }
        public void setError(boolean isError) { this.isError = isError; }
    }

    public static class ContentBlock {
        private String type;
        private String text;

        public ContentBlock() {}
        public ContentBlock(String type, String text) {
            this.type = type;
            this.text = text;
        }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }

        public static ContentBlock text(String text) {
            return new ContentBlock("text", text);
        }
    }

    // ===================== Resource Definitions =====================

    public static class ResourceDefinition {
        private String uri;
        private String name;
        private String description;
        private String mimeType;

        public ResourceDefinition() {}
        public String getUri() { return uri; }
        public void setUri(String uri) { this.uri = uri; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getMimeType() { return mimeType; }
        public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    }

    public static class ResourceListResult {
        private List<ResourceDefinition> resources;
        public ResourceListResult() {}
        public List<ResourceDefinition> getResources() { return resources; }
        public void setResources(List<ResourceDefinition> resources) { this.resources = resources; }
    }

    public static class ResourceReadResult {
        private List<ResourceContent> contents;
        public ResourceReadResult() {}
        public List<ResourceContent> getContents() { return contents; }
        public void setContents(List<ResourceContent> contents) { this.contents = contents; }
    }

    public static class ResourceContent {
        private String uri;
        private String mimeType;
        private String text;

        public ResourceContent() {}
        public ResourceContent(String uri, String mimeType, String text) {
            this.uri = uri;
            this.mimeType = mimeType;
            this.text = text;
        }

        public String getUri() { return uri; }
        public void setUri(String uri) { this.uri = uri; }
        public String getMimeType() { return mimeType; }
        public void setMimeType(String mimeType) { this.mimeType = mimeType; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
    }
}
