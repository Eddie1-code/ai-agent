package com.xcw.aiagentbackend.controller;

import com.xcw.aiagentbackend.common.BaseResponse;
import com.xcw.aiagentbackend.common.ResultUtils;
import com.xcw.aiagentbackend.model.auth.ChangePasswordRequest;
import com.xcw.aiagentbackend.model.auth.UpdateProfileRequest;
import com.xcw.aiagentbackend.model.auth.UserAccount;
import com.xcw.aiagentbackend.model.auth.UserProfileResponse;
import com.xcw.aiagentbackend.service.AuthService;
import jakarta.annotation.Resource;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private AuthService authService;

    @GetMapping("/me")
    public BaseResponse<UserProfileResponse> getProfile(Authentication authentication) {
        UserAccount user = authService.getCurrentUser(String.valueOf(authentication.getPrincipal()));
        return ResultUtils.success(toProfile(user));
    }

    @PutMapping("/me")
    public BaseResponse<UserProfileResponse> updateProfile(@RequestBody UpdateProfileRequest request,
                                                           Authentication authentication) {
        UserAccount user = authService.updateProfile(
                String.valueOf(authentication.getPrincipal()),
                request.getNickname(),
                request.getAvatarUrl()
        );
        return ResultUtils.success(toProfile(user));
    }

    @PostMapping("/password")
    public BaseResponse<Boolean> changePassword(@RequestBody ChangePasswordRequest request,
                                                Authentication authentication) {
        authService.changePassword(
                String.valueOf(authentication.getPrincipal()),
                request.getOldPassword(),
                request.getNewPassword()
        );
        return ResultUtils.success(true);
    }

    private UserProfileResponse toProfile(UserAccount user) {
        return UserProfileResponse.builder()
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatarUrl(user.getAvatarUrl())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
