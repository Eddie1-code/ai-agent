package com.xcw.aiagentbackend.app;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

@SpringBootTest
class MentorAppTest {

    @Resource
    private MentorApp mentorApp;

    @Test
    void testChat() {
        String chatId = UUID.randomUUID().toString();
        String message = "你好，我是Eddie";
        String answer = mentorApp.doChat(message, chatId);
        Assertions.assertNotNull(answer);
        message = "我正在学习 Java，但计划执行总是中断";
        answer = mentorApp.doChat(message, chatId);
        Assertions.assertNotNull(answer);
        message = "结合我们前面的对话，帮我总结下一步行动";
        answer = mentorApp.doChat(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithReport() {
        String chatId = UUID.randomUUID().toString();
        String message = "你好，我是Eddie，我最近在学习和作息管理上都很混乱";
        MentorApp.PlanReport planReport = mentorApp.doChatWithReport(message, chatId);
        Assertions.assertNotNull(planReport);
    }

    @Test
    void doChatWithRag() {
        String chatId = UUID.randomUUID().toString();
        String message = "我最近注意力分散，如何建立稳定的学习节奏？";
        String answer = mentorApp.doChatWithRag(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithTools() {
        testMessage("周末去上海，帮我规划低预算的一天路线");
        testMessage("帮我抓取一个公开网页并提炼行动建议");
        testMessage("下载一张适合学习海报风格的背景图");
        testMessage("执行 Python3 脚本来生成数据分析报告");
        testMessage("保存我的学习计划为文件");
        testMessage("生成一份‘下周学习计划’PDF，包含任务列表和风险提醒");
    }

    private void testMessage(String message) {
        String chatId = UUID.randomUUID().toString();
        String answer = mentorApp.doChatWithTools(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithMcp() {
        String chatId = UUID.randomUUID().toString();
        String message = "帮我搜索一些提升专注力的示意图片";
        String answer = mentorApp.doChatWithMcp(message, chatId);
        Assertions.assertNotNull(answer);
    }
}
