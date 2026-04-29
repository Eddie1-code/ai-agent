package com.xcw.aiagentbackend.controller;

import com.xcw.aiagentbackend.common.BaseResponse;
import com.xcw.aiagentbackend.common.ResultUtils;
import com.xcw.aiagentbackend.config.ApiSecurityProperties;
import com.xcw.aiagentbackend.model.auth.AuthTokenResponse;
import com.xcw.aiagentbackend.model.auth.LoginRequest;
import com.xcw.aiagentbackend.model.auth.RegisterRequest;
import com.xcw.aiagentbackend.model.auth.UserAccount;
import com.xcw.aiagentbackend.service.AuthService;
import com.xcw.aiagentbackend.service.JwtService;
import jakarta.annotation.Resource;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Resource
    private AuthService authService;

    @Resource
    private JwtService jwtService;
    @Resource
    private ApiSecurityProperties apiSecurityProperties;

    @PostMapping("/register")
    public BaseResponse<AuthTokenResponse> register(@RequestBody RegisterRequest request) {
        UserAccount user = authService.register(request.getUsername(), request.getPassword());
        String token = jwtService.generateToken(user.getUsername());
        return ResultUtils.success(AuthTokenResponse.builder()
                .token(token)
                .expireInSeconds(apiSecurityProperties.getJwtExpireSeconds())
                .username(user.getUsername())
                .build());
    }

    @PostMapping("/login")
    public BaseResponse<AuthTokenResponse> login(@RequestBody LoginRequest request) {
        UserAccount user = authService.login(request.getUsername(), request.getPassword());
        String token = jwtService.generateToken(user.getUsername());
        return ResultUtils.success(AuthTokenResponse.builder()
                .token(token)
                .expireInSeconds(apiSecurityProperties.getJwtExpireSeconds())
                .username(user.getUsername())
                .build());
    }

    @GetMapping("/me")
    public BaseResponse<String> me(Authentication authentication) {
        return ResultUtils.success(authentication == null ? "" : String.valueOf(authentication.getPrincipal()));
    }
}
