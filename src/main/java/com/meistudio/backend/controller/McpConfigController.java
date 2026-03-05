package com.meistudio.backend.controller;

import com.meistudio.backend.annotation.RateLimit;
import com.meistudio.backend.common.Result;
import com.meistudio.backend.common.UserContext;
import com.meistudio.backend.entity.McpServer;
import com.meistudio.backend.mcp.client.McpClientManager;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * MCP 服务器配置控制器。
 *
 * 提供 MCP Server 的增删查改 RESTful 接口，供鸿蒙客户端的"设置→MCP 服务管理"页面调用。
 * 用户通过此接口添加外部 MCP Server 后，Agent 会在下次对话时自动发现并使用这些工具。
 *
 * 安全机制：
 * - JWT 鉴权：由 AuthInterceptor 统一拦截
 * - 接口限流：添加操作限制每分钟 5 次，防止滥用
 * - 用户隔离：每个用户只能操作自己的 MCP Server 配置
 */
@RestController
@RequestMapping("/api/mcp/servers")
@RequiredArgsConstructor
public class McpConfigController {

    private final McpClientManager mcpClientManager;

    /**
     * 添加 MCP Server。
     *
     * POST /api/mcp/servers
     * Body: { "name": "我的Notion", "url": "https://mcp.example.com/notion" }
     */
    @PostMapping
    @RateLimit(maxRequests = 5, windowSeconds = 60, message = "MCP 服务添加过于频繁，请稍后再试")
    public Result<McpServer> addServer(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        String url = body.get("url");

        if (name == null || name.isBlank()) {
            return Result.error(400, "服务名称不能为空");
        }
        if (url == null || url.isBlank()) {
            return Result.error(400, "服务地址不能为空");
        }

        Long userId = UserContext.getUserId();
        McpServer server = mcpClientManager.addServer(userId, name, url);
        return Result.success(server);
    }

    /**
     * 查询当前用户的所有 MCP Server。
     *
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
     *
     * DELETE /api/mcp/servers/{id}
     */
    @DeleteMapping("/{id}")
    public Result<Void> removeServer(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        mcpClientManager.removeServer(userId, id);
        return Result.success();
    }

    /**
     * 手动重连指定的 MCP Server（用于健康检查失败后的手动恢复）。
     *
     * POST /api/mcp/servers/{id}/reconnect
     */
    @PostMapping("/{id}/reconnect")
    public Result<McpServer> reconnectServer(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        McpServer server = mcpClientManager.reconnectServer(userId, id);
        return Result.success(server);
    }
}
