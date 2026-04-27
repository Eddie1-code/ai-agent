package com.xcw.aiagentbackend.model.chat;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StreamEvent {
    private String requestId;
    private long seq;
    private String eventType;
    private String content;
    private boolean done;
}
