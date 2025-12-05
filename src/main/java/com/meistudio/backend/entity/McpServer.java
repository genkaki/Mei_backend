package com.meistudio.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * MCP 服务器配置实体。
 *
 * 存储用户配置的外部 MCP Server 连接信息。
 * 每个用户可以添加多个 MCP Server，Agent 在创建时会自动发现并注入这些 Server 提供的工具。
 */
@Data
@TableName("mcp_server")
public class McpServer {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属用户 ID */
    private Long userId;

    /** 服务名称（用户自定义，如"我的Notion"） */
    private String name;

    /** MCP Server 的 SSE 端点地址 */
    private String url;

    /** 连接状态：1=在线，0=离线 */
    private Integer status;

    /** 已发现的工具数量 */
    private Integer toolCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
