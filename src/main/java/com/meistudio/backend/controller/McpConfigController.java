package com.meistudio.backend.controller;

import com.meistudio.backend.annotation.RateLimit;
import com.meistudio.backend.common.Result;
import com.meistudio.backend.common.UserContext;
import com.meistudio.backend.entity.McpServer;
import com.meistudio.backend.mcp.client.McpClientManager;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * MCP 服务器配置控制器。
 *
 * 提供 MCP Server 的增删查改 RESTful 接口，供鸿蒙客户端的"设置→MCP 服务管理"页面调用。
 * 用户通过此接口添加外部 MCP Server 后，Agent 会在下次对话时自动发现并使用这些工具。
 *
 * 支持丰富的配置选项：
 * - name: 服务名称（必填）
 * - url: 服务端点地址（必填）
 * - description: 服务描述（可选）
 * - type: 传输类型 streamableHttp | sse（默认 streamableHttp）
 * - apiKey: API Key（可选，自动转为 Authorization: Bearer xxx）
 * - headers: 自定义 Headers JSON（可选，高级用户使用）
 */
@RestController
@RequestMapping("/api/mcp/servers")
public class McpConfigController {

    private final McpClientManager mcpClientManager;

    public McpConfigController(McpClientManager mcpClientManager) {
        this.mcpClientManager = mcpClientManager;
    }

    /**
     * 添加 MCP Server。
     *
     * POST /api/mcp/servers
     * Body: {
     *   "name": "阿里云 Z Image",
     *   "url": "https://dashscope.aliyuncs.com/api/v1/mcps/zimage/mcp",
     *   "description": "高效图像生成模型",
     *   "type": "streamableHttp",
     *   "apiKey": "sk-xxx",
     *   "headers": "{\"X-Custom\": \"value\"}"
     * }
     */
    @PostMapping
    @RateLimit(maxRequests = 5, windowSeconds = 60, message = "MCP 服务添加过于频繁，请稍后再试")
    public Result<McpServer> addServer(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        String url = (String) body.get("url");

        if (name == null || name.isBlank()) {
            return Result.error(400, "服务名称不能为空");
        }
        if (url == null || url.isBlank()) {
            return Result.error(400, "服务地址不能为空");
        }

        String description = (String) body.get("description");
        String type = (String) body.get("type");
        String apiKey = (String) body.get("apiKey");
        String headers = (String) body.get("headers");

        Long userId = UserContext.getUserId();
        McpServer server = mcpClientManager.addServer(userId, name, url, description, type, headers, apiKey);
        return Result.success(server);
    }

    /**
     * 查询当前用户的所有 MCP Server。
     * GET /api/mcp/servers
     */
    @GetMapping
    public Result<List<McpServer>> listServers() {
        Long userId = UserContext.getUserId();
        List<McpServer> servers = mcpClientManager.listServers(userId);
        return Result.success(servers);
    }

    /**
     * 删除指定的 MCP Server。
     * DELETE /api/mcp/servers/{id}
     */
    @DeleteMapping("/{id}")
    public Result<Void> removeServer(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        mcpClientManager.removeServer(userId, id);
        return Result.success();
    }

    /**
     * 手动重连指定的 MCP Server。
     * POST /api/mcp/servers/{id}/reconnect
     */
    @PostMapping("/{id}/reconnect")
    public Result<McpServer> reconnectServer(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        McpServer server = mcpClientManager.reconnectServer(userId, id);
        return Result.success(server);
    }

    /**
     * 切换 MCP Server 的激活状态（启用/禁用）。
     * POST /api/mcp/servers/{id}/toggle
     * Body: { "active": true/false }
     */
    @PostMapping("/{id}/toggle")
    public Result<McpServer> toggleServer(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Boolean active = (Boolean) body.get("active");
        if (active == null) {
            return Result.error(400, "请指定启用/禁用状态");
        }

        Long userId = UserContext.getUserId();
        McpServer server = mcpClientManager.toggleActive(userId, id, active);
        return Result.success(server);
    }
}
