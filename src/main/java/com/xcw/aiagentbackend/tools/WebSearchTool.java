package com.xcw.aiagentbackend.tools;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.github.rholder.retry.RetryException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Slf4j
public class WebSearchTool {

    // SearchAPI 的搜索接口地址
    private static final String SEARCH_API_URL = "https://www.searchapi.io/api/v1/search";

    private final String apiKey;
    public WebSearchTool(String apiKey) {
        this.apiKey = apiKey;
    }

    @Tool(description = "Search for information from Baidu Search Engine")
    public String searchWeb(
        @ToolParam(description = "Search query keyword") String query) {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("q", query);
        paramMap.put("api_key", apiKey);
        paramMap.put("engine", "baidu");
        long start = System.currentTimeMillis();
        try {
            String response = RetryExecutor.execute(() -> HttpUtil.get(SEARCH_API_URL, paramMap));
            // 取出返回结果的前 5 条
            JSONObject jsonObject = JSONUtil.parseObj(response);
            // 提取 organic_results 部分
            JSONArray organicResults = jsonObject.getJSONArray("organic_results");
            if (organicResults == null || organicResults.isEmpty()) {
                return "Error searching Baidu: 搜索结果为空";
            }
            int limit = Math.min(5, organicResults.size());
            List<Object> objects = organicResults.subList(0, limit);
            // 拼接搜索结果为字符串
            String result = objects.stream().map(obj -> {
                JSONObject tmpJSONObject = (JSONObject) obj;
                return tmpJSONObject.toString();
            }).collect(Collectors.joining(","));
            log.info("WebSearchTool success costMs={} query={}", System.currentTimeMillis() - start, query);
            return result;
        } catch (ExecutionException | RetryException e) {
            log.warn("WebSearchTool retry exhausted costMs={} query={}", System.currentTimeMillis() - start, query);
            return "Error searching Baidu after retries: " + e.getMessage();
        } catch (Exception e) {
            log.error("WebSearchTool failed costMs={} query={}", System.currentTimeMillis() - start, query, e);
            return "Error searching Baidu: " + e.getMessage();
        }
    }
}