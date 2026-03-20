package com.meistudio.backend.interceptor;

import com.meistudio.backend.common.UserContext;
import com.meistudio.backend.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT authentication interceptor.
 * Extracts the token from the Authorization header, validates it,
 * and stores the user ID in ThreadLocal via UserContext.
 * Always calls UserContext.remove() in afterCompletion to prevent memory leaks.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    public AuthInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"msg\":\"未登录或Token已过期\"}");
            return false;
        }

        String token = authHeader.substring(7);
        Long userId = jwtUtil.parseToken(token);
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"msg\":\"Token无效\"}");
            return false;
        }

        // Store user ID in ThreadLocal for downstream use
        UserContext.setUserId(userId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // CRITICAL: Remove ThreadLocal to prevent memory leaks in thread pools
        UserContext.remove();
    }
}
