package com.xcw.aiagentbackend.model.chat;

import lombok.Data;

@Data
public class CreateChatSessionRequest {
    private String title;
    private String mode;
}
