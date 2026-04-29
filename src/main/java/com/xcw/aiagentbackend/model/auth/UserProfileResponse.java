package com.xcw.aiagentbackend.model.auth;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserProfileResponse {
    private String username;
    private String nickname;
    private String avatarUrl;
    private LocalDateTime createdAt;
}
