package com.xcw.aiagentbackend.agent;


import com.xcw.aiagentbackend.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

@Component
public class XuManus extends ToolCallAgent {

    public XuManus(ToolCallback[] allTools, ChatModel dashscopeChatModel) {
        super(allTools);
        this.setName("xuManus");
        String SYSTEM_PROMPT = "You are XuManus, an all-capable AI assistant, aimed at solving any task "
                + "presented by the user. You have various tools at your disposal that you can call upon "
                + "to efficiently complete complex requests.";
        this.setSystemPrompt(SYSTEM_PROMPT);
        String NEXT_STEP_PROMPT = "Based on user needs, proactively select the most appropriate tool "
                + "or combination of tools. For complex tasks, break down the problem and use different "
                + "tools step by step. After each tool, clearly explain the result and suggest next steps. "
                + "If you want to stop interaction, use the terminate tool/function call.";
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        this.setMaxSteps(20);
        // 初始化客户端
        ChatClient chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        this.setChatClient(chatClient);
    }
}
