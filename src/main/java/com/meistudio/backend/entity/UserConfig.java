package com.meistudio.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户 AI 配置实体类。
 * 存储用户私有的 API Key、模型选择及 RAG 参数。
 */
@Data
@TableName("user_config")
public class UserConfig {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String dashscopeApiKey;

    private String embeddingModel;

    private String chatModel;

    private Integer chunkSize;

    private Integer chunkOverlap;

    private Integer topK;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
