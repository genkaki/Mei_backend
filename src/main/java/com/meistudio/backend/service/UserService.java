package com.meistudio.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meistudio.backend.entity.User;
import com.meistudio.backend.mapper.UserMapper;
import com.meistudio.backend.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;

    /**
     * Login or auto-register by device ID.
     * If the device has never logged in before, a new user record is created.
     * Returns a map containing the JWT token and user ID.
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
