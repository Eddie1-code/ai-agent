package com.xcw.aiagentbackend.tools;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

class WebScrapingToolTest {

    @Test
    void scrapeWebPage() {
        WebScrapingTool tool = new WebScrapingTool();
        String result = tool.scrapeWebPage("https://www.codefather.cn");
        assertNotNull(result);
        assertTrue(result.contains("<html"));
    }
}