package com.meistudio.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meistudio.backend.entity.McpServer;
import org.apache.ibatis.annotations.Mapper;

/**
 * MCP 服务器配置 Mapper。
 * 继承 MyBatis-Plus BaseMapper，自动获得 CRUD 能力。
 */
@Mapper
public interface McpServerMapper extends BaseMapper<McpServer> {
}
