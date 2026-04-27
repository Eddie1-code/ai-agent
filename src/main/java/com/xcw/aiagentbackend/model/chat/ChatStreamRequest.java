package com.xcw.aiagentbackend.model.chat;

import lombok.Data;

@Data
public class ChatStreamRequest {
    private String message;
    private String chatId;
    private String requestId;
    private String mode;
}
