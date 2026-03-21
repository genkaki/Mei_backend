package com.meistudio.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meistudio.backend.entity.UserConfig;
import com.meistudio.backend.mapper.UserConfigMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 用户配置服务。
 * 负责获取和更新用户的个性化 AI 配置。
 */
@Service
public class UserConfigService {

    private final UserConfigMapper userConfigMapper;
    private final com.meistudio.backend.mapper.UserMapper userMapper;

    // 系统默认配置 (来自 application.yml)
    @Value("${dashscope.api-key:}")
    private String defaultApiKey;

    @Value("${knowledge.chunk-size:800}")
    private int defaultChunkSize;

    @Value("${knowledge.chunk-overlap:100}")
    private int defaultChunkOverlap;

    public UserConfigService(UserConfigMapper userConfigMapper, com.meistudio.backend.mapper.UserMapper userMapper) {
        this.userConfigMapper = userConfigMapper;
        this.userMapper = userMapper;
    }

    public boolean isWebUser(Long userId) {
        com.meistudio.backend.entity.User user = userMapper.selectById(userId);
        return user != null && user.getEmail() != null && !user.getEmail().isBlank();
    }

    public UserConfig getUserConfig(Long userId) {
        // 1. 获取用户信息以判断来源
        boolean isWebUser = isWebUser(userId);

        UserConfig config = userConfigMapper.selectOne(
                new LambdaQueryWrapper<UserConfig>().eq(UserConfig::getUserId, userId)
        );

        if (config == null) {
            config = new UserConfig();
            config.setUserId(userId);
            // 核心逻辑改动：UserConfig 对象不再提供系统 key 兜底。
            // 这样依赖 UserConfig 的对话 (Chat) 和 插件 (MCP) 就会强制走 BYOK 路径。
            config.setDashscopeApiKey(null);
            config.setEmbeddingModel("text-embedding-v2");
            config.setChatModel("qwen-turbo");
            config.setChunkSize(defaultChunkSize);
            config.setChunkOverlap(defaultChunkOverlap);
            config.setTopK(5);
        } else {
            // 即使数据库里有记录，如果是空字符串也强制转为 null，确保上层判定一致
            if (config.getDashscopeApiKey() != null && config.getDashscopeApiKey().isBlank()) {
                config.setDashscopeApiKey(null);
            }
        }
        return config;
    }

    /**
     * 保存或更新用户配置。
     */
    public void saveUserConfig(Long userId, UserConfig newConfig) {
        UserConfig existing = userConfigMapper.selectOne(
                new LambdaQueryWrapper<UserConfig>().eq(UserConfig::getUserId, userId)
        );

        newConfig.setUserId(userId);
        if (existing == null) {
            userConfigMapper.insert(newConfig);
        } else {
            newConfig.setId(existing.getId());
            userConfigMapper.updateById(newConfig);
        }
    }
}
