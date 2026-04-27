package com.xcw.aiagentbackend.service;

import com.xcw.aiagentbackend.agent.XuManus;
import com.xcw.aiagentbackend.app.LoveApp;
import com.xcw.aiagentbackend.model.chat.MentorMode;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Arrays;

@Service
public class MentorChatService {

    @Resource
    private LoveApp loveApp;

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ChatModel dashscopeChatModel;

    public Flux<String> chatByStream(MentorMode mode, String message, String chatId) {
        if (mode == MentorMode.PLANNER) {
            XuManus xuManus = new XuManus(allTools, dashscopeChatModel);
            return Flux.fromIterable(Arrays.asList(xuManus.run(message).split("\n")));
        }
        return loveApp.doChatByStream(message, chatId);
    }
}
