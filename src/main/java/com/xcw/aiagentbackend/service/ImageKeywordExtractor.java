package com.xcw.aiagentbackend.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ImageKeywordExtractor {

    private static final Pattern BULLET_PLACE = Pattern.compile(
            "^[-*•]\\s*(.+?)(?:\\s{2,}|\\s+——|\\s+-\\s+|：|:).*$");
    private static final Pattern BOLD_PLACE = Pattern.compile("\\*\\*([^*\\n]{2,30})\\*\\*");
    private static final Pattern NON_PLACE = Pattern.compile(
            "建议|提醒|告知|注意|预算|时间|交通|着装|穿|携带|记得|风险|准备|提前|策略|完成标准|下一步|需求|便利性|动线|核心|目标|方案|亮点");

    @Autowired
    private ChatModel dashscopeChatModel;

    public List<String> extractPlaces(String userMessage, String planText, int maxCount) {
        LinkedHashSet<String> places = new LinkedHashSet<>();
        collectFromText(userMessage, places, maxCount);
        if (places.size() < maxCount && planText != null && !planText.isBlank()) {
            collectFromText(planText, places, maxCount);
        }
        if (places.size() < 2) {
            places.addAll(extractByLlm(userMessage, maxCount));
        }
        if (places.isEmpty() && userMessage != null && !userMessage.isBlank()) {
            places.add(fallbackKeyword(userMessage));
        }
        return places.stream().limit(maxCount).toList();
    }

    private void collectFromText(String text, LinkedHashSet<String> places, int maxCount) {
        if (text == null || text.isBlank()) {
            return;
        }
        for (String line : text.split("\\R")) {
            if (places.size() >= maxCount) {
                return;
            }
            String trimmed = line.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            Matcher bulletMatcher = BULLET_PLACE.matcher(trimmed);
            if (bulletMatcher.matches()) {
                addPlace(places, bulletMatcher.group(1));
                continue;
            }
            Matcher boldMatcher = BOLD_PLACE.matcher(trimmed);
            while (boldMatcher.find() && places.size() < maxCount) {
                addPlace(places, boldMatcher.group(1));
            }
        }
    }

    private List<String> extractByLlm(String userMessage, int maxCount) {
        if (userMessage == null || userMessage.isBlank()) {
            return List.of();
        }
        try {
            String prompt = """
                    从用户需求中提取最多 %d 个适合搜索实景图片的中文地点/场景关键词（每个 2-8 字）。
                    只保留真实地点或场景（如公园、书店、咖啡馆、景点、街道、餐厅），丢弃"着装建议""交通方式""预算""时间"等非地点短语。
                    只输出 JSON 字符串数组，不要解释，例如：["图书馆","城市公园","河边步道"]
                    用户需求：%s
                    """.formatted(maxCount, userMessage);
            String response = ChatClient.builder(dashscopeChatModel)
                    .build()
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            return parseJsonArray(response, maxCount);
        } catch (Exception e) {
            log.warn("image_keyword_llm_failed reason={}", e.toString());
            return List.of();
        }
    }

    private List<String> parseJsonArray(String response, int maxCount) {
        if (response == null || response.isBlank()) {
            return List.of();
        }
        int start = response.indexOf('[');
        int end = response.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return List.of();
        }
        try {
            JSONArray array = JSONUtil.parseArray(response.substring(start, end + 1));
            List<String> result = new ArrayList<>();
            for (Object item : array) {
                if (item == null) {
                    continue;
                }
                String value = String.valueOf(item).trim();
                if (value.length() >= 2 && value.length() <= 30) {
                    result.add(value);
                }
                if (result.size() >= maxCount) {
                    break;
                }
            }
            return result.stream().filter(s -> !s.isBlank()).limit(maxCount).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private void addPlace(LinkedHashSet<String> places, String raw) {
        if (raw == null) {
            return;
        }
        String cleaned = raw.replaceAll("[（(][^（）()]*[）)]", "")
                .replaceAll("^[-*•\\s]+", "")
                .replaceAll("\\*+", "")
                .trim();
        if (cleaned.length() < 2 || cleaned.length() > 30) {
            return;
        }
        if (NON_PLACE.matcher(cleaned).find()) {
            return;
        }
        places.add(cleaned);
    }

    private String fallbackKeyword(String userMessage) {
        String text = userMessage.replaceAll("\\s+", "");
        if (text.contains("放空")) {
            return "城市休闲空间";
        }
        if (text.contains("图片") || text.contains("配图") || text.contains("照片")) {
            return "城市景观";
        }
        return text.length() > 12 ? text.substring(0, 12) : text;
    }
}
