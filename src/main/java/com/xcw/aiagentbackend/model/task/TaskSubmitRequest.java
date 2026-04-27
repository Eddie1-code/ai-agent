package com.xcw.aiagentbackend.model.task;

import lombok.Data;

@Data
public class TaskSubmitRequest {
    private String mode;
    private String message;
    private String chatId;
}
