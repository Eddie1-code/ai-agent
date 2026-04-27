package com.xcw.aiagentbackend.tools;


import org.springframework.ai.tool.annotation.Tool;

public class TerminateTool {

    @Tool(description = "Terminate the interaction when the request is met or when the assistant cannot proceed "
            + "further with the task. When all tasks are finished, call this tool to end the work.")
    public String doTerminate() {
        return "任务结束";
    }
}
