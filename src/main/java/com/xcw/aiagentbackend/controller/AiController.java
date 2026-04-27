package com.xcw.aiagentbackend.controller;
import com.xcw.aiagentbackend.common.BaseResponse;
import com.xcw.aiagentbackend.common.ResultUtils;
import com.xcw.aiagentbackend.model.chat.ChatStreamRequest;
import com.xcw.aiagentbackend.model.chat.MentorMode;
import com.xcw.aiagentbackend.model.chat.StreamEvent;
import com.xcw.aiagentbackend.service.MentorChatService;
import com.xcw.aiagentbackend.service.StreamSessionManager;
import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private MentorChatService mentorChatService;

    @Resource
    private StreamSessionManager streamSessionManager;

    @GetMapping(value = "/mentor/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMentorChat(String message, String chatId, String mode, String requestId) {
        ChatStreamRequest request = new ChatStreamRequest();
        request.setMessage(message);
        request.setChatId(chatId);
        request.setMode(mode);
        request.setRequestId(requestId);
        return streamMentorChatByRequest(request);
    }

    @PostMapping(value = "/mentor/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMentorChatByRequest(@RequestBody ChatStreamRequest request) {
        String reqId = (request.getRequestId() == null || request.getRequestId().isBlank())
                ? UUID.randomUUID().toString()
                : request.getRequestId();
        MentorMode mentorMode = MentorMode.fromValue(request.getMode());
        SseEmitter emitter = new SseEmitter(300000L);

        streamSessionManager.registerSignal(reqId);
        sendEvent(emitter, StreamEvent.builder()
                .requestId(reqId)
                .seq(streamSessionManager.nextSeq(reqId))
                .eventType("meta")
                .content("mode=" + mentorMode.name())
                .done(false)
                .build());

        Flux<String> stream = mentorChatService.chatByStream(mentorMode, request.getMessage(), request.getChatId());
        streamSessionManager.setSubscription(reqId, stream.subscribe(chunk -> {
            if (!streamSessionManager.isCancelled(reqId)) {
                sendEvent(emitter, StreamEvent.builder()
                        .requestId(reqId)
                        .seq(streamSessionManager.nextSeq(reqId))
                        .eventType(mentorMode == MentorMode.PLANNER ? "thinking" : "answer")
                        .content(chunk)
                        .done(false)
                        .build());
            }
        }, error -> {
            sendEvent(emitter, StreamEvent.builder()
                    .requestId(reqId)
                    .seq(streamSessionManager.nextSeq(reqId))
                    .eventType("error")
                    .content(error.getMessage())
                    .done(true)
                    .build());
            streamSessionManager.cleanup(reqId);
            emitter.completeWithError(error);
        }, () -> {
            sendEvent(emitter, StreamEvent.builder()
                    .requestId(reqId)
                    .seq(streamSessionManager.nextSeq(reqId))
                    .eventType("done")
                    .content("")
                    .done(true)
                    .build());
            streamSessionManager.cleanup(reqId);
            emitter.complete();
        }));

        emitter.onTimeout(() -> {
            streamSessionManager.cancel(reqId);
            streamSessionManager.cleanup(reqId);
            emitter.complete();
        });
        emitter.onCompletion(() -> streamSessionManager.cleanup(reqId));
        return emitter;
    }

    @PostMapping("/mentor/chat/stop")
    public BaseResponse<Boolean> stopMentorChat(@RequestBody ChatStreamRequest request) {
        if (request.getRequestId() == null || request.getRequestId().isBlank()) {
            return new BaseResponse<>(HttpStatus.BAD_REQUEST.value(), false, "requestId 不能为空");
        }
        boolean stopped = streamSessionManager.cancel(request.getRequestId());
        return ResultUtils.success(stopped);
    }

    // 兼容旧接口，统一代理到新入口
    @GetMapping(value = "/love_app/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter doChatWithLoveAppSSE(String message, String chatId) {
        return streamMentorChat(message, chatId, "coach", null);
    }

    @GetMapping(value = "/manus/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter doChatWithManus(String message) {
        return streamMentorChat(message, null, "planner", null);
    }

    private void sendEvent(SseEmitter emitter, StreamEvent event) {
        try {
            emitter.send(event);
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }
}