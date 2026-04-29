package com.xcw.aiagentbackend.model.auth;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthTokenResponse {
    private String token;
    private long expireInSeconds;
    private String username;
}
