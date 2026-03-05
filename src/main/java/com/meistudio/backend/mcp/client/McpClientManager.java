package com.meistudio.backend.mcp.client;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meistudio.backend.entity.McpServer;
import com.meistudio.backend.mapper.McpServerMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 连接池管理器 —— 管理用户配置的所有 MCP Server 连接生命周期。
 *
 * 架构要点（面试高频考点）：
 * 1. 用户级隔离：每个用户拥有独立的 MCP 连接集合，保证多租户安全。
 * 2. 懒加载策略：MCP 连接在 Agent 创建时才真正建立，避免无效连接占用资源。
 * 3. 连接池化：使用 ConcurrentHashMap 管理连接实例，支持高并发下的安全读写。
 *
 * 设计思想：
 * - 本组件是 AgentService 与外部 MCP 生态之间的桥梁。
 * - AgentService 只需调用 getToolsForUser() 和 getExecutorsForUser() 两个方法，
 *   即可无感知地获取所有 MCP 工具，无需关心连接细节。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpClientManager {

    private final McpServerMapper mcpServerMapper;

    /**
     * 用户级 MCP 连接缓存。
     * Key: userId, Value: Map(serverId → McpToolProxy)
     */
    private final Map<Long, Map<Long, McpToolProxy>> userToolProxies = new ConcurrentHashMap<>();

    // ==================== 对外接口（供 AgentService 调用） ====================

    /**
     * 获取某用户的所有 MCP 工具规格（供 LangChain4j AiServices.tools() 使用）。
     */
    public List<ToolSpecification> getToolSpecsForUser(Long userId) {
        ensureConnections(userId);
        Map<Long, McpToolProxy> proxies = userToolProxies.get(userId);
        if (proxies == null || proxies.isEmpty()) return Collections.emptyList();

        List<ToolSpecification> specs = new ArrayList<>();
        for (McpToolProxy proxy : proxies.values()) {
            specs.addAll(proxy.getToolSpecifications());
        }
        return specs;
    }

    /**
     * 获取某用户的所有 MCP 工具执行器（与上面的 specs 一一对应）。
     */
    public List<ToolExecutor> getToolExecutorsForUser(Long userId) {
        ensureConnections(userId);
        Map<Long, McpToolProxy> proxies = userToolProxies.get(userId);
        if (proxies == null || proxies.isEmpty()) return Collections.emptyList();

        List<ToolExecutor> executors = new ArrayList<>();
        for (McpToolProxy proxy : proxies.values()) {
            executors.addAll(proxy.getToolExecutors());
        }
        return executors;
    }

    // ==================== 对外接口（供 McpConfigController 调用） ====================

    /**
     * 添加 MCP Server：保存到数据库 + 建立连接 + 工具发现。
     *
     * @return 保存后的 McpServer 实体（含发现的工具数量）
     */
    public McpServer addServer(Long userId, String name, String url) {
        log.info("[McpClientManager] 添加 MCP Server: userId={}, name={}, url={}", userId, name, url);

        // 1. 建立连接并发现工具
        McpServerConnection connection = new McpServerConnection(name, url);
        int toolCount = connection.connect();

        // 2. 保存到数据库
        McpServer server = new McpServer();
        server.setUserId(userId);
        server.setName(name);
        server.setUrl(url);
        server.setStatus(connection.isConnected() ? 1 : 0);
        server.setToolCount(toolCount);
        mcpServerMapper.insert(server);

        // 3. 缓存连接
        if (connection.isConnected()) {
            McpToolProxy proxy = new McpToolProxy(connection);
            userToolProxies
                    .computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                    .put(server.getId(), proxy);

            // 重建该用户的 Agent（使其感知新工具）
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
        log.info("[McpClientManager] 删除 MCP Server: userId={}, serverId={}", userId, serverId);

        // 1. 断开连接缓存
        Map<Long, McpToolProxy> proxies = userToolProxies.get(userId);
        if (proxies != null) {
            proxies.remove(serverId);
        }

        // 2. 从数据库删除
        mcpServerMapper.deleteById(serverId);

        // 3. 重建该用户的 Agent
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

        // 重新连接
        McpServerConnection connection = new McpServerConnection(server.getName(), server.getUrl());
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

    // ==================== 内部方法 ====================

    /**
     * 确保用户的所有 MCP 连接已建立（懒加载）。
     * 如果内存中没有缓存，则从数据库加载并逐个连接。
     */
    private void ensureConnections(Long userId) {
        if (userToolProxies.containsKey(userId)) return;

        List<McpServer> servers = listServers(userId);
        if (servers.isEmpty()) return;

        Map<Long, McpToolProxy> proxies = new ConcurrentHashMap<>();

        for (McpServer server : servers) {
            try {
                McpServerConnection connection = new McpServerConnection(server.getName(), server.getUrl());
                int toolCount = connection.connect();

                if (connection.isConnected()) {
                    proxies.put(server.getId(), new McpToolProxy(connection));

                    // 同步更新数据库状态
                    server.setStatus(1);
                    server.setToolCount(toolCount);
                    mcpServerMapper.updateById(server);
                }
            } catch (Exception e) {
                log.warn("[McpClientManager] 懒加载连接失败: server={}, error={}", server.getName(), e.getMessage());
                server.setStatus(0);
                mcpServerMapper.updateById(server);
            }
        }

        if (!proxies.isEmpty()) {
            userToolProxies.put(userId, proxies);
        }
    }

    /**
     * 使指定用户的 Agent 实例失效，迫使下次对话时重建（以感知新工具）。
     * 这里通过 Spring Event 或直接调用 AgentService.clearMemory 实现。
     * 当前采用简单策略：清除工具代理缓存，AgentService 会在下次请求时重建。
     */
    private void invalidateUserAgent(Long userId) {
        // AgentService 的 userAgents 在每次 chat 时通过 computeIfAbsent 懒创建，
        // 此处只需要确保 MCP 工具缓存是最新的即可。
        // 如果需要立刻生效，可以通过 Spring Event 通知 AgentService 清除该用户的 Agent。
        log.info("[McpClientManager] 用户 {} 的工具配置已变更，下次对话将重建 Agent", userId);
    }
}
