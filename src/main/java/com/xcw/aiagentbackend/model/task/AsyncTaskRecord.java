package com.xcw.aiagentbackend.model.task;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AsyncTaskRecord {
    private String taskId;
    private String mode;
    private String status;
    private String requestPayload;
    private String resultPayload;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
