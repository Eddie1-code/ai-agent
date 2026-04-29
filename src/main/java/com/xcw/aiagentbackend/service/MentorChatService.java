package com.xcw.aiagentbackend.service;

import com.xcw.aiagentbackend.app.MentorApp;
import com.xcw.aiagentbackend.model.chat.MentorMode;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class MentorChatService {

    @Resource
    private MentorApp mentorApp;

    public Flux<String> chatByStream(MentorMode mode, String message, String chatId) {
        if (mode == MentorMode.PLANNER) {
            return chatPlannerWithAutoImage(message, chatId);
        }
        if (shouldUseMcpTools(message)) {
            String result = mentorApp.doChatWithMcp(message, chatId);
            return Flux.fromArray(result.split("\n"));
        }
        if (shouldUseImageTools(message)) {
            String result = mentorApp.doChatWithTools(message, chatId);
            return Flux.fromArray(result.split("\n"));
        }
        return mentorApp.doChatByStream(message, chatId);
    }

    private Flux<String> chatPlannerWithAutoImage(String message, String chatId) {
        Flux<String> planStream = mentorApp.doPlannerPlanByStream(message, chatId);
        if (!shouldUseImageTools(message)) {
            return planStream;
        }
        String imagePrompt = buildAutoImagePrompt(message);
        Mono<String> imageResult = Mono.fromSupplier(() -> mentorApp.doChatWithTools(imagePrompt, chatId));
        return planStream.concatWith(imageResult.flatMapMany(result -> Flux.fromArray(result.split("\n"))));
    }

    private boolean shouldUseMcpTools(String message) {
        if (message == null) {
            return false;
        }
        String text = message.toLowerCase();
        return text.contains("地图")
                || text.contains("路线")
                || text.contains("附近");
    }

    private boolean shouldUseImageTools(String message) {
        if (message == null) {
            return false;
        }
        String text = message.toLowerCase();
        return text.contains("配图")
                || text.contains("封面图")
                || text.contains("海报")
                || text.contains("插画")
                || text.contains("生成图片")
                || text.contains("图文并茂");
    }

    private String buildAutoImagePrompt(String userMessage) {
        return """
                请基于用户需求自动生成1张配图，必须调用图片生成工具。
                要求：
                1) prompt 清晰包含场景、氛围、构图；
                2) resolution 固定为 1024:1024；
                3) 返回可访问图片地址；
                4) 如果生成失败，返回失败原因和一条可执行的替代建议。

                用户原始需求：
                """ + userMessage;
    }
}
