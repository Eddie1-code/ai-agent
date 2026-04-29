package com.xcw.aiagentbackend.model.task;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AsyncTaskRecord {
    private String taskId;
    private String requestId;
    private String mode;
    private String status;
    private String ownerType;
    private String ownerId;
    private String requestPayload;
    private String resultPayload;
    private String errorMessage;
    private String cancelReason;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime updatedAt;
}
