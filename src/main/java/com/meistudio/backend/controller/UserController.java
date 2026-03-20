package com.meistudio.backend.controller;

import com.meistudio.backend.annotation.RateLimit;
import com.meistudio.backend.common.Result;
import com.meistudio.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Web 端邮箱登录。
     * POST /api/user/login-email
     */
    @PostMapping("/login-email")
    @RateLimit(maxRequests = 10, windowSeconds = 60, message = "登录过于频繁，请稍后再试")
    public Result<Map<String, Object>> loginEmail(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String password = body.get("password");
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return Result.error(400, "邮箱和密码不能为空");
        }
        return Result.success("登录成功", userService.loginByEmail(email, password));
    }

    /**
     * Web 端邮箱注册。
     * POST /api/user/register
     */
    @PostMapping("/register")
    @RateLimit(maxRequests = 5, windowSeconds = 60, message = "注册过于频繁，请稍后再试")
    public Result<Void> register(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String password = body.get("password");
        String code = body.get("code");
        if (email == null || email.isBlank() || password == null || password.isBlank() || code == null || code.isBlank()) {
            return Result.error(400, "邮箱、密码和验证码不能为空");
        }
        userService.register(email, password, code);
        return Result.success("注册成功", null);
    }

    /**
     * 发送邮箱验证码。
     * GET /api/user/send-code?email=xxx
     */
    @GetMapping("/send-code")
    @RateLimit(maxRequests = 3, windowSeconds = 60, message = "发送太频繁，请稍后再试")
    public Result<Void> sendCode(@RequestParam String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            return Result.error(400, "请提供有效的邮箱地址");
        }
        userService.sendVerificationCode(email);
        return Result.success("验证码已发送", null);
    }

    /**
     * 兼容鸿蒙端的原设备 ID 登录。
     * POST /api/user/login
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
