package com.xcw.aiagentbackend.model.chat;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ChatMessageRecord {
    private Long id;
    private String sessionId;
    private Long userId;
    private String role;
    private String eventType;
    private String content;
    private String metadataJson;
    private LocalDateTime createdAt;
}
