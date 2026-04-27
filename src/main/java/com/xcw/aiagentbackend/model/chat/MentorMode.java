package com.xcw.aiagentbackend.model.chat;

public enum MentorMode {
    COACH,
    PLANNER;

    public static MentorMode fromValue(String value) {
        if (value == null || value.isBlank()) {
            return COACH;
        }
        return MentorMode.valueOf(value.trim().toUpperCase());
    }
}
