package com.xcw.aiagentbackend.model.apikey;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ApiKeyRecord {
    private Long id;
    private String apiKey;
    private String owner;
    private String status;
    private Integer quota;
    private Integer usedCount;
    private LocalDateTime expiredAt;
    private LocalDateTime createdAt;
}
