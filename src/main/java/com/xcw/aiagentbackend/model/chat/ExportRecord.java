package com.xcw.aiagentbackend.model.chat;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ExportRecord {
    private Long id;
    private Long userId;
    private String sessionId;
    private Long sourceMessageId;
    private String filePath;
    private LocalDateTime createdAt;
}
