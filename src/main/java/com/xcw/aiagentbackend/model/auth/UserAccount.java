package com.xcw.aiagentbackend.model.auth;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserAccount {
    private Long id;
    private String username;
    private String nickname;
    private String avatarUrl;
    private String passwordHash;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
