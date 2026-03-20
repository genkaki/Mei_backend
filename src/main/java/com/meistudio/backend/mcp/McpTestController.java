package com.meistudio.backend.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 * 专门用于客户端测试的虚拟 MCP Server 端点。
 * 兼容客户端极简的直连 POST JSON-RPC 传输规范。
 */
@RestController
@RequestMapping("/api/mcp/test-server")
public class McpTestController {
    private static final Logger log = LoggerFactory.getLogger(McpTestController.class);

    @PostMapping
    public Map<String, Object> handleRequest(@RequestBody McpProtocol.JsonRpcRequest request) {
        log.info("[McpTestServer] 收到客户端测试请求 method: {}", request.getMethod());

        Map<String, Object> response = new HashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", request.getId());

        switch (request.getMethod()) {
            case "initialize":
                response.put("result", Map.of(
                        "protocolVersion", "2025-03-26",
                        "capabilities", Map.of()
                ));
                break;
            case "tools/list":
                response.put("result", Map.of(
                        "tools", List.of(
                                Map.of(
                                        "name", "get_current_weather",
                                        "description", "获取指定城市的实时天气情况（仅供测试）",
                                        "inputSchema", Map.of(
                                                "type", "object",
                                                "properties", Map.of(
                                                        "city", Map.of("type", "string", "description", "城市名，如北京")
                                                ),
                                                "required", List.of("city")
                                        )
                                ),
                                Map.of(
                                        "name", "execute_system_command",
                                        "description", "模拟执行底层系统命令获取系统状态",
                                        "inputSchema", Map.of(
                                                "type", "object",
                                                "properties", Map.of(
                                                        "command", Map.of("type", "string")
                                                )
                                        )
                                )
                        )
                ));
                break;
            case "tools/call":
                Map<String, Object> params = (Map<String, Object>) request.getParams();
                String toolName = (String) params.get("name");
                Map<String, Object> args = (Map<String, Object>) params.get("arguments");

                String fakeResult = "";
                if ("get_current_weather".equals(toolName)) {
                    fakeResult = "天气接口模拟返回：" + args.get("city") + " 今天晴空万里，气温 25°C，非常适合出门捉 Bug。";
                } else {
                    fakeResult = "系统命令模拟结果：Command executed successfully. Memory usage 42%.";
                }

                response.put("result", Map.of(
                        "content", List.of(
                                Map.of(
                                        "type", "text",
                                        "text", fakeResult
                                )
                        )
                ));
                break;
            default:
                response.put("result", Map.of());
        }

        return response;
    }
}
