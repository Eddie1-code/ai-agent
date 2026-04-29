package com.xcw.aiagentbackend.model.chat;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ChatSessionRecord {
    private String id;
    private Long userId;
    private String title;
    private String mode;
    private Boolean archived;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
