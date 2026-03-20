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

    public UserConfig getUserConfig(Long userId) {
        // 1. 获取用户信息以判断来源 (Web 用户需强制 BYOK)
        com.meistudio.backend.entity.User user = userMapper.selectById(userId);
        boolean isWebUser = user != null && user.getEmail() != null && !user.getEmail().isBlank();

        UserConfig config = userConfigMapper.selectOne(
                new LambdaQueryWrapper<UserConfig>().eq(UserConfig::getUserId, userId)
        );

        if (config == null) {
            config = new UserConfig();
            config.setUserId(userId);
            // Web 用户不提供系统默认 Key 兜底
            config.setDashscopeApiKey(isWebUser ? null : defaultApiKey);
            config.setEmbeddingModel("text-embedding-v2");
            config.setChatModel("qwen-turbo");
            config.setChunkSize(defaultChunkSize);
            config.setChunkOverlap(defaultChunkOverlap);
            config.setTopK(5);
        } else {
            // 如果 Web 用户没有设置私有 Key，则保持为空，不使用默认 Key
            if (isWebUser) {
                // 不做任何操作，保持原样（即使用户设置了空字符串也视为无效）
            } else if (config.getDashscopeApiKey() == null || config.getDashscopeApiKey().isBlank()) {
                config.setDashscopeApiKey(defaultApiKey);
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
