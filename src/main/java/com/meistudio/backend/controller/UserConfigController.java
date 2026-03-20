package com.meistudio.backend.controller;

import com.meistudio.backend.common.Result;
import com.meistudio.backend.common.UserContext;
import com.meistudio.backend.entity.UserConfig;
import com.meistudio.backend.service.UserConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户配置控制器。
 */
@RestController
@RequestMapping("/api/user/config")
@RequiredArgsConstructor
public class UserConfigController {

    private final UserConfigService userConfigService;

    /**
     * 获取当前登录用户的 AI 配置。
     */
    @GetMapping
    public Result<UserConfig> getConfig() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return Result.error(401, "未登录或会话超时");
        }
        UserConfig config = userConfigService.getUserConfig(userId);
        // 服务端脱敏，防止 API 响应泄露完整 Key
        config.setDashscopeApiKey(maskKey(config.getDashscopeApiKey()));
        return Result.success(config);
    }

    /**
     * 更新当前登录用户的 AI 配置。
     */
    @PostMapping
    public Result<Void> updateConfig(@RequestBody UserConfig config) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return Result.error(401, "未登录或会话超时");
        }
        userConfigService.saveUserConfig(userId, config);
        return Result.success("配置已更新", null);
    }

    private String maskKey(String key) {
        if (key == null || key.length() < 8) return "****";
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}
