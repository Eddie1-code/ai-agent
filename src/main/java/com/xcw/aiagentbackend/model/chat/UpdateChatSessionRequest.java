package com.xcw.aiagentbackend.model.chat;

import lombok.Data;

@Data
public class UpdateChatSessionRequest {
    private String title;
    private Boolean archived;
}
