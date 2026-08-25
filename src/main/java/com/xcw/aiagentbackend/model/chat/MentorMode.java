package com.xcw.aiagentbackend.model.chat;

public enum MentorMode {
    COACH,
    PLANNER;

    public static MentorMode fromValue(String value) {
        if (value == null || value.isBlank()) {
            return COACH;
        }
        try {
            return MentorMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            // 未知模式（如历史/异常值）回退到 COACH，避免非法枚举在 SSE 同步阶段抛异常
            return COACH;
        }
    }
}
