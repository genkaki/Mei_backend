package com.meistudio.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * MCP 服务器配置实体。
 *
 * 存储用户配置的外部 MCP Server 连接信息。
 * 每个用户可以添加多个 MCP Server，Agent 在创建时会自动发现并注入这些 Server 提供的工具。
 *
 * 支持丰富的配置选项以兼容各平台 MCP 服务：
 * - streamableHttp（阿里云百炼、Cherry Studio 等）
 * - sse（传统 SSE 端点）
 * - 自定义 Headers（Bearer Token、API Key 等鉴权方式）
 */
@TableName("mcp_server")
public class McpServer {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    /** 所属用户 ID */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long userId;

    /** 服务名称（用户自定义，如"阿里云 Z Image"） */
    private String name;

    /** 服务描述（可选，如"高效图像生成模型"） */
    private String description;

    /** MCP Server 的端点地址 */
    private String url;

    /**
     * 传输类型：streamableHttp | sse
     * 默认 streamableHttp，兼容大多数现代 MCP 服务
     */
    private String type;

    /**
     * 自定义 HTTP Headers（JSON 格式）。
     * 例如：{"Authorization": "Bearer sk-xxx", "X-Custom": "value"}
     * 用于鉴权和自定义请求头。
     */
    private String headers;

    /**
     * API Key（便捷字段，自动转换为 Authorization: Bearer {apiKey}）。
     * 如果同时设置了 headers 中的 Authorization，则 headers 优先。
     */
    private String apiKey;

    /** 连接状态：1=在线，0=离线 */
    private Integer status;

    /** 已发现的工具数量 */
    private Integer toolCount;

    /** 该 MCP 服务是否为激活状态（用户可以临时禁用） */
    private Boolean active;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    // ==================== Getter / Setter ====================
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getHeaders() { return headers; }
    public void setHeaders(String headers) { this.headers = headers; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getToolCount() { return toolCount; }
    public void setToolCount(Integer toolCount) { this.toolCount = toolCount; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
