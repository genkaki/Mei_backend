package com.meistudio.backend.mcp.client;

import com.meistudio.backend.mcp.McpProtocol;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.service.tool.ToolExecutor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具代理 —— 将 MCP Server 的远程工具适配为 LangChain4j 的 ToolExecutor。
 *
 * 架构要点（面试高频考点）：
 * 1. 适配器模式 (Adapter Pattern)：
 *    MCP 协议定义的工具接口与 LangChain4j 的 ToolExecutor 接口不兼容。
 *    本类作为适配器，将 MCP 的 tools/call JSON-RPC 调用封装为 LangChain4j 可识别的标准 Tool。
 *    这使得 Agent 无需感知工具的来源（本地 @Tool 还是远程 MCP），实现了"工具来源无关"的统一调度。
 *
 * 2. 动态工具注册：
 *    与传统的 @Tool 注解（编译期绑定）不同，MCP 工具在运行时通过 tools/list 动态发现。
 *    这实现了 Agent 能力的"热插拔"——用户添加新的 MCP Server 后，Agent 无需重启即可获得新工具。
 */
@Slf4j
public class McpToolProxy {

    private final McpServerConnection connection;

    @Getter
    private final List<ToolSpecification> toolSpecifications;

    @Getter
    private final List<ToolExecutor> toolExecutors;

    public McpToolProxy(McpServerConnection connection) {
        this.connection = connection;
        this.toolSpecifications = new ArrayList<>();
        this.toolExecutors = new ArrayList<>();

        // 将 MCP 工具定义转换为 LangChain4j 的 ToolSpecification + ToolExecutor
        for (McpProtocol.ToolDefinition mcpTool : connection.getDiscoveredTools()) {
            ToolSpecification spec = convertToSpec(mcpTool);
            ToolExecutor executor = createExecutor(mcpTool.getName());

            toolSpecifications.add(spec);
            toolExecutors.add(executor);

            log.debug("[McpToolProxy] 已注册 MCP 工具: {}", mcpTool.getName());
        }
    }

    /**
     * 将 MCP ToolDefinition 转换为 LangChain4j ToolSpecification。
     *
     * LangChain4j 的 ToolSpecification 本质上是一个 JSON Schema，
     * 描述了函数名、描述、参数类型等信息，大模型根据这些信息决定何时调用该工具。
     */
    private ToolSpecification convertToSpec(McpProtocol.ToolDefinition mcpTool) {
        ToolSpecification.Builder builder = ToolSpecification.builder()
                .name(mcpTool.getName())
                .description(mcpTool.getDescription() != null ? mcpTool.getDescription() : "MCP 工具");

        // 将 MCP 的 inputSchema 直接透传为 LangChain4j 的 ToolParameters
        // MCP 和 LangChain4j 都使用 JSON Schema 格式，天然兼容
        if (mcpTool.getInputSchema() != null) {
            Map<String, Object> schema = mcpTool.getInputSchema();
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
            @SuppressWarnings("unchecked")
            List<String> required = (List<String>) schema.getOrDefault("required", List.of());

            if (properties != null && !properties.isEmpty()) {
                // 将 MCP 的 properties 转换为 LangChain4j 需要的格式
                Map<String, Map<String, Object>> toolProperties = new HashMap<>();
                for (Map.Entry<String, Object> entry : properties.entrySet()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> propValue = (Map<String, Object>) entry.getValue();
                    toolProperties.put(entry.getKey(), propValue);
                }

                ToolParameters params = ToolParameters.builder()
                        .properties(toolProperties)
                        .required(required)
                        .build();

                builder.parameters(params);
            }
        }

        return builder.build();
    }

    /**
     * 为指定的 MCP 工具创建一个 ToolExecutor。
     * 当 Agent 决定调用此工具时，ToolExecutor 会通过 McpServerConnection 发送 JSON-RPC 请求。
     */
    private ToolExecutor createExecutor(String toolName) {
        return (request, memoryId) -> {
            log.info("[McpToolProxy] Agent 调用 MCP 工具: tool={}, args={}", toolName, request.arguments());
            String result = connection.callTool(toolName, request.arguments());
            log.info("[McpToolProxy] MCP 工具返回: tool={}, 结果长度={}", toolName, result.length());
            return result;
        };
    }
}
