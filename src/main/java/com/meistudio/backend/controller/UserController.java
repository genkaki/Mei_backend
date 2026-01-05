package com.meistudio.backend.controller;

import com.meistudio.backend.annotation.RateLimit;
import com.meistudio.backend.common.Result;
import com.meistudio.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Login or auto-register by device ID.
     * POST /api/user/login
     * Body: { "deviceId": "xxx" }
     */
    @PostMapping("/login")
    @RateLimit(maxRequests = 5, windowSeconds = 60, message = "登录过于频繁，请稍后再试")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String deviceId = body.get("deviceId");
        if (deviceId == null || deviceId.isBlank()) {
            return Result.error(400, "deviceId不能为空");
        }
        Map<String, Object> data = userService.loginByDeviceId(deviceId);
        return Result.success("登录成功", data);
    }
}
