package com.meistudio.backend.mcp.client;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meistudio.backend.entity.McpServer;
import com.meistudio.backend.mapper.McpServerMapper;
import com.meistudio.backend.service.AgentService;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;
import com.meistudio.backend.mcp.McpProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 连接池管理器 —— 管理用户配置的所有 MCP Server 连接生命周期。
 *
 * 架构要点：
 * 1. 用户级隔离：每个用户拥有独立的 MCP 连接集合，保证多租户安全。
 * 2. 每次对话前检查连接：对比数据库和内存缓存，自动补建缺失的连接。
 * 3. 容错设计：单个 MCP 连接失败不影响其他服务的正常使用。
 */
@Service
public class McpClientManager {
    private static final Logger log = LoggerFactory.getLogger(McpClientManager.class);

    private final McpServerMapper mcpServerMapper;
    private final AgentService agentService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public McpClientManager(McpServerMapper mcpServerMapper, @Lazy AgentService agentService) {
        this.mcpServerMapper = mcpServerMapper;
        this.agentService = agentService;
    }

    /**
     * 用户级 MCP 连接缓存。
     * Key: userId, Value: Map(serverId → McpToolProxy)
     */
    private final Map<Long, Map<Long, McpToolProxy>> userToolProxies = new ConcurrentHashMap<>();

    // ==================== 对外接口（供 AgentService 调用） ====================

    /**
     * 获取某用户的所有 MCP 工具规格。
     */
    public List<ToolSpecification> getToolSpecsForUser(Long userId, List<Long> serverIds) {
        ensureConnections(userId);
        Map<Long, McpToolProxy> proxies = userToolProxies.get(userId);
        if (proxies == null || proxies.isEmpty()) return Collections.emptyList();

        List<ToolSpecification> specs = new ArrayList<>();
        for (Map.Entry<Long, McpToolProxy> entry : proxies.entrySet()) {
            // 如果指定了服务器列表，且当前服务器不在列表中，则跳过
            if (serverIds != null && !serverIds.isEmpty() && !serverIds.contains(entry.getKey())) {
                continue;
            }
            specs.addAll(entry.getValue().getToolSpecifications());
        }
        return specs;
    }

    /**
     * 获取某用户的所有 MCP 工具执行器（与上面的 specs 一一对应）。
     */
    public List<ToolExecutor> getToolExecutorsForUser(Long userId, List<Long> serverIds) {
        ensureConnections(userId);
        Map<Long, McpToolProxy> proxies = userToolProxies.get(userId);
        if (proxies == null || proxies.isEmpty()) return Collections.emptyList();

        List<ToolExecutor> executors = new ArrayList<>();
        for (Map.Entry<Long, McpToolProxy> entry : proxies.entrySet()) {
            // 如果指定了服务器列表，且当前服务器不在列表中，则跳过
            if (serverIds != null && !serverIds.isEmpty() && !serverIds.contains(entry.getKey())) {
                continue;
            }
            executors.addAll(entry.getValue().getToolExecutors());
        }
        return executors;
    }

    // ==================== 对外接口（供 McpConfigController 调用） ====================

    /**
     * 添加 MCP Server：保存到数据库 + 建立连接 + 工具发现。
     */
    public McpServer addServer(Long userId, String name, String url, String description,
                               String type, String headers, String apiKey) {
        log.info("[McpClientManager] 添加 MCP Server: userId={}, name={}, url={}, type={}",
                userId, name, url, type);

        // 1. 解析自定义 Headers
        Map<String, String> headerMap = buildHeaderMap(headers, apiKey);

        // 2. 建立连接并发现工具
        McpServerConnection connection = new McpServerConnection(name, url, headerMap);
        int toolCount = connection.connect();

        // 3. 保存到数据库
        McpServer server = new McpServer();
        server.setUserId(userId);
        server.setName(name);
        server.setDescription(description);
        server.setUrl(url);
        server.setType(type != null && !type.isBlank() ? type : "streamableHttp");
        server.setHeaders(headers);
        server.setApiKey(apiKey);
        server.setStatus(connection.isConnected() ? 1 : 0);
        server.setToolCount(toolCount);
        server.setActive(true);
        mcpServerMapper.insert(server);

        // 4. 缓存连接
        if (connection.isConnected()) {
            McpToolProxy proxy = new McpToolProxy(connection);
            userToolProxies
                    .computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                    .put(server.getId(), proxy);

            // 重建该用户的 Agent
            invalidateUserAgent(userId);
        }

        log.info("[McpClientManager] MCP Server 添加完成: id={}, tools={}, status={}",
                server.getId(), toolCount, connection.isConnected() ? "在线" : "离线");

        return server;
    }

    /**
     * 获取某用户的所有 MCP Server。
     */
    public List<McpServer> listServers(Long userId) {
        return mcpServerMapper.selectList(
                new LambdaQueryWrapper<McpServer>()
                        .eq(McpServer::getUserId, userId)
                        .orderByDesc(McpServer::getCreatedAt)
        );
    }

    /**
     * 删除 MCP Server：断开连接 + 从数据库删除。
     */
    public void removeServer(Long userId, Long serverId) {
        log.info("[McpClientManager] 删除 MCP Server 请求: userId={}, serverId={}", userId, serverId);

        Map<Long, McpToolProxy> proxies = userToolProxies.get(userId);
        if (proxies != null) {
            proxies.remove(serverId);
        }

        McpServer server = mcpServerMapper.selectById(serverId);
        if (server != null && server.getUserId().equals(userId)) {
            log.info("[McpClientManager] 找到服务器记录: {}, 执行删除", server.getName());
            mcpServerMapper.deleteById(serverId);
        } else {
            log.warn("[McpClientManager] 未找到或无权操作 ID={} 的记录", serverId);
        }

        invalidateUserAgent(userId);
    }

    /**
     * 手动重连指定的 MCP Server。
     */
    public McpServer reconnectServer(Long userId, Long serverId) {
        McpServer server = mcpServerMapper.selectById(serverId);
        if (server == null || !server.getUserId().equals(userId)) {
            throw new RuntimeException("MCP Server 不存在或无权限");
        }

        // 解析 Headers
        Map<String, String> headerMap = buildHeaderMap(server.getHeaders(), server.getApiKey());

        // 重新连接
        McpServerConnection connection = new McpServerConnection(server.getName(), server.getUrl(), headerMap);
        int toolCount = connection.connect();

        // 更新数据库
        server.setStatus(connection.isConnected() ? 1 : 0);
        server.setToolCount(toolCount);
        mcpServerMapper.updateById(server);

        // 更新缓存
        if (connection.isConnected()) {
            McpToolProxy proxy = new McpToolProxy(connection);
            userToolProxies
                    .computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                    .put(serverId, proxy);
        }

        invalidateUserAgent(userId);
        return server;
    }

    /**
     * 切换 MCP Server 的激活状态。
     */
    public McpServer toggleActive(Long userId, Long serverId, boolean active) {
        McpServer server = mcpServerMapper.selectById(serverId);
        if (server == null || !server.getUserId().equals(userId)) {
            throw new RuntimeException("MCP Server 不存在或无权限");
        }

        server.setActive(active);
        mcpServerMapper.updateById(server);

        // 如果禁用了，移除缓存中的连接
        if (!active) {
            Map<Long, McpToolProxy> proxies = userToolProxies.get(userId);
            if (proxies != null) {
                proxies.remove(serverId);
            }
        }

        invalidateUserAgent(userId);
        return server;
    }

    /**
     * 测试指定的 MCP Server。
     * 只有测试通过（能发现工具）才返回成功，并附带工具列表。
     */
    public List<McpProtocol.ToolDefinition> testServer(Long userId, Long serverId) {
        log.info("[McpClientManager] 测试 MCP Server: userId={}, serverId={}", userId, serverId);
        McpServer server = mcpServerMapper.selectById(serverId);
        if (server == null || !server.getUserId().equals(userId)) {
            throw new RuntimeException("MCP Server 不存在或无权限");
        }

        // 解析 Headers
        Map<String, String> headerMap = buildHeaderMap(server.getHeaders(), server.getApiKey());

        // 执行握手和工具列表查询
        McpServerConnection connection = new McpServerConnection(server.getName(), server.getUrl(), headerMap);
        connection.connect(); // 如果失败会直接抛出包含详细错误信息的 RuntimeException
        
        return connection.getDiscoveredTools();
    }

    // ==================== 内部方法 ====================

    /**
     * 将 JSON headers 字符串和 apiKey 合并为 Map。
     * apiKey 会自动转为 "Authorization: Bearer {apiKey}"，但 headers 中的同名键优先。
     */
    private Map<String, String> buildHeaderMap(String headersJson, String apiKey) {
        Map<String, String> headerMap = new HashMap<>();

        // 1. 获取数据库中存储的 API Key
        String finalApiKey = apiKey;
        
        // 判定准则：插件 (MCP) 必须遵循 strict BYOK 原则。
        // 如果数据库里的 Key 是空的，或者是特定的测试占位符，不论是网页端还是鸿蒙端，统统不进行回退。
        // 这确保了插件资源的成本由用户自行承担。
        String placeholder = "sk-6666666666666666666666666";
        if (finalApiKey == null || finalApiKey.isBlank() || finalApiKey.equals(placeholder)) {
            log.warn("[McpClientManager] 插件密钥缺失或为占位符 (isPlaceholder={})", finalApiKey != null && finalApiKey.equals(placeholder));
            finalApiKey = null;
        } else {
            log.info("[McpClientManager] 使用实时输入的私有密钥 (Len={})", finalApiKey.length());
        }

        // 2. 如果有密钥，设置为 Authorization Header
        if (finalApiKey != null && !finalApiKey.isBlank()) {
            headerMap.put("Authorization", "Bearer " + finalApiKey);
        }

        // 3. 如果有自定义 headers JSON，解析并进行环境变量替换
        if (headersJson != null && !headersJson.isBlank()) {
            try {
                String processedJson = headersJson;
                if (finalApiKey != null) {
                    // 🎯 核心逻辑：自动替换官方文档模板中的占位符
                    processedJson = processedJson.replace("${DASHSCOPE_API_KEY}", finalApiKey)
                                                .replace("$DASHSCOPE_API_KEY", finalApiKey);
                }
                
                Map<String, String> parsed = objectMapper.readValue(processedJson, new TypeReference<>() {});
                headerMap.putAll(parsed);
            } catch (Exception e) {
                log.warn("[McpClientManager] 解析自定义 Headers 失败: {}", e.getMessage());
            }
        }

        // 4. 如果没有显式的 Authorization/api-key，且有私人密钥，进行补充
        if (finalApiKey != null && !finalApiKey.isBlank()) {
             if (!headerMap.containsKey("Authorization")) {
                 headerMap.put("Authorization", "Bearer " + finalApiKey);
             }
             if (!headerMap.containsKey("api-key")) {
                 headerMap.put("api-key", finalApiKey);
             }
        }

        return headerMap;
    }

    /**
     * 确保用户的所有 MCP 连接已建立。
     * 
     * 核心逻辑（修复重启丢失问题）：
     * 1. 每次调用都从数据库查询 active=true 的服务器列表
     * 2. 对比内存缓存，找到缺失的连接并补建
     * 3. 单个连接失败不影响其他服务，仅标记为离线
     */
    private void ensureConnections(Long userId) {
        List<McpServer> activeServers = mcpServerMapper.selectList(
                new LambdaQueryWrapper<McpServer>()
                        .eq(McpServer::getUserId, userId)
                        .eq(McpServer::getActive, true)
                        .orderByDesc(McpServer::getCreatedAt)
        );

        if (activeServers.isEmpty()) return;

        Map<Long, McpToolProxy> existingProxies = userToolProxies.computeIfAbsent(userId, k -> new ConcurrentHashMap<>());

        for (McpServer server : activeServers) {
            // 已有连接的跳过
            if (existingProxies.containsKey(server.getId())) {
                continue;
            }

            // 缺失连接 → 尝试建立
            try {
                log.info("[McpClientManager] 恢复连接: name={}, url={}", server.getName(), server.getUrl());
                Map<String, String> headerMap = buildHeaderMap(server.getHeaders(), server.getApiKey());
                McpServerConnection connection = new McpServerConnection(server.getName(), server.getUrl(), headerMap);
                int toolCount = connection.connect();

                if (connection.isConnected()) {
                    existingProxies.put(server.getId(), new McpToolProxy(connection));
                    server.setStatus(1);
                    server.setToolCount(toolCount);
                    mcpServerMapper.updateById(server);
                    log.info("[McpClientManager] 恢复成功: name={}, 工具数={}", server.getName(), toolCount);
                } else {
                    log.warn("[McpClientManager] 恢复失败（离线）: name={}", server.getName());
                    server.setStatus(0);
                    mcpServerMapper.updateById(server);
                }
            } catch (Exception e) {
                log.warn("[McpClientManager] 恢复连接异常: server={}, error={}", server.getName(), e.getMessage());
                server.setStatus(0);
                mcpServerMapper.updateById(server);
                // 不抛异常 → 继续处理下一个服务器
            }
        }
    }

    private void invalidateUserAgent(Long userId) {
        log.info("[McpClientManager] 用户 {} 的工具配置已变更，正在清理 Agent 缓存以同步工具列表", userId);
        agentService.clearMemory(userId);
    }
}
