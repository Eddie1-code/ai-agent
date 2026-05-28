package com.xcw.aiagentbackend.model.auth;

import lombok.Data;

@Data
public class ResetPasswordRequest {
    private String username;
    private String captchaKey;
    private String captchaCode;
    private String newPassword;
}
