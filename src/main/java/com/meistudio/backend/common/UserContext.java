package com.meistudio.backend.common;

/**
 * ThreadLocal-based user context.
 * Stores the current user's ID for the lifetime of a single request thread.
 * IMPORTANT: Always call remove() after the request completes to prevent memory leaks.
 */
public class UserContext {

    private static final ThreadLocal<Long> CURRENT_USER_ID = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        CURRENT_USER_ID.set(userId);
    }

    public static Long getUserId() {
        return CURRENT_USER_ID.get();
    }

    public static void remove() {
        CURRENT_USER_ID.remove();
    }
}
