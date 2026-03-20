package com.meistudio.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meistudio.backend.entity.User;
import com.meistudio.backend.mapper.UserMapper;
import com.meistudio.backend.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
public class UserService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final StringRedisTemplate redisTemplate;

    @org.springframework.beans.factory.annotation.Value("${spring.mail.username:your-email@qq.com}")
    private String mailFrom;

    public UserService(UserMapper userMapper, JwtUtil jwtUtil, PasswordEncoder passwordEncoder,
                       JavaMailSender mailSender, StringRedisTemplate redisTemplate) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Web 端邮箱注册。
     */
    public void register(String email, String password, String code) {
        // 1. 验证码校验
        String redisKey = "verify:code:" + email;
        String cachedCode = redisTemplate.opsForValue().get(redisKey);
        if (cachedCode == null || !cachedCode.equals(code)) {
            throw new RuntimeException("验证码无效或已过期");
        }

        // 2. 检查邮箱是否已存在
        User existingUser = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email)
        );
        if (existingUser != null) {
            throw new RuntimeException("该邮箱已被注册");
        }

        // 2. 创建新用户并加密密码
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        userMapper.insert(user);
        
        // 注册成功，移除验证码
        redisTemplate.delete(redisKey);
    }

    /**
     * 发送邮箱验证码。
     */
    public void sendVerificationCode(String email) {
        // 1. 生成 6 位验证码
        String code = String.format("%06d", new Random().nextInt(1000000));
        
        // 2. 存入 Redis (5 分钟有效)
        String redisKey = "verify:code:" + email;
        redisTemplate.opsForValue().set(redisKey, code, 5, TimeUnit.MINUTES);
        
        // 3. 发送邮件
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("MeiStudio <" + mailFrom + ">"); // 动态获取 yml 配置中的邮箱
            message.setTo(email);
            message.setSubject("MeiStudio 注册验证码");
            message.setText("您的验证码是: " + code + " (5分钟内有效)。如非本人操作请忽略。");
            mailSender.send(message);
            System.out.println("[邮件发送成功] 目标=" + email + ", 验证码=" + code);
        } catch (Exception e) {
            // 如果邮件发送失败（如账户未配置），在控制台打印以便于测试
            System.err.println("[邮件中心] 发送失败(请检查 yml 配置): " + e.getMessage());
            System.out.println(">>> [测试模式] 验证码已生成并存入 Redis: " + code + " (请直接使用此码注册)");
        }
    }

    /**
     * Web 端邮箱登录。
     */
    public Map<String, Object> loginByEmail(String email, String password) {
        // 1. 查找用户
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email)
        );
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 2. 校验密码
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("密码错误");
        }

        // 3. 生成 JWT Token
        String token = jwtUtil.generateToken(user.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("userId", user.getId());
        result.put("email", user.getEmail());
        return result;
    }

    /**
     * Login or auto-register by device ID (用于鸿蒙端兼容).
     */
    public Map<String, Object> loginByDeviceId(String deviceId) {
        // Check if this device already has an account
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getDeviceId, deviceId)
        );

        // Auto-register if first time
        if (user == null) {
            user = new User();
            user.setDeviceId(deviceId);
            userMapper.insert(user);
        }

        // Generate JWT token
        String token = jwtUtil.generateToken(user.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("userId", user.getId());
        return result;
    }
}
