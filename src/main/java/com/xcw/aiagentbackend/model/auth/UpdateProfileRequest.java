package com.xcw.aiagentbackend.model.auth;

import lombok.Data;

@Data
public class UpdateProfileRequest {
    private String nickname;
    private String avatarUrl;
}
