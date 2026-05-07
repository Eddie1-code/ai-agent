package com.xcw.aiagentbackend.controller;

import com.xcw.aiagentbackend.common.BaseResponse;
import com.xcw.aiagentbackend.common.ResultUtils;
import com.xcw.aiagentbackend.model.chat.ChatMessageRecord;
import com.xcw.aiagentbackend.model.chat.ChatSessionRecord;
import com.xcw.aiagentbackend.model.chat.CreateChatSessionRequest;
import com.xcw.aiagentbackend.model.chat.UpdateChatSessionRequest;
import com.xcw.aiagentbackend.service.ChatSessionService;
import jakarta.annotation.Resource;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/chat/sessions")
public class ChatSessionController {

    @Resource
    private ChatSessionService chatSessionService;

    @GetMapping
    public BaseResponse<List<ChatSessionRecord>> listSessions(Authentication authentication) {
        return ResultUtils.success(chatSessionService.listSessions(String.valueOf(authentication.getPrincipal())));
    }

    @PostMapping
    public BaseResponse<ChatSessionRecord> createSession(@RequestBody CreateChatSessionRequest request,
                                                         Authentication authentication) {
        return ResultUtils.success(chatSessionService.createSession(
                String.valueOf(authentication.getPrincipal()),
                request.getTitle(),
                request.getMode()
        ));
    }

    @PutMapping("/{sessionId}")
    public BaseResponse<ChatSessionRecord> updateSession(@PathVariable String sessionId,
                                                         @RequestBody UpdateChatSessionRequest request,
                                                         Authentication authentication) {
        return ResultUtils.success(chatSessionService.updateSession(
                String.valueOf(authentication.getPrincipal()),
                sessionId,
                request.getTitle(),
                request.getArchived(),
                request.getPinned()
        ));
    }

    @DeleteMapping("/{sessionId}")
    public BaseResponse<Boolean> deleteSession(@PathVariable String sessionId, Authentication authentication) {
        chatSessionService.deleteSession(String.valueOf(authentication.getPrincipal()), sessionId);
        return ResultUtils.success(true);
    }

    @GetMapping("/{sessionId}/messages")
    public BaseResponse<List<ChatMessageRecord>> listMessages(@PathVariable String sessionId,
                                                              @RequestParam(defaultValue = "50") int limit,
                                                              Authentication authentication) {
        return ResultUtils.success(chatSessionService.listMessages(
                String.valueOf(authentication.getPrincipal()),
                sessionId,
                limit
        ));
    }
}
