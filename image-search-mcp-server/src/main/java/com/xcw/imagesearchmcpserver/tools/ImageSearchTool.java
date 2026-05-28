package com.xcw.imagesearchmcpserver.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ImageSearchTool {

    private static final String API_URL = "https://api.pexels.com/v1/search";

    @Value("${pexels.api-key:}")
    private String apiKey;

    @Tool(description = "从 Pexels 搜索真实图片")
    public String searchImage(@ToolParam(description = "搜索关键词") String query) {
        try {
            return String.join(",", searchMediumImages(query));
        } catch (Exception e) {
            return "图片检索失败：" + e.getMessage();
        }
    }

    public List<String> searchMediumImages(String query) {
        if (StrUtil.isBlank(query) || StrUtil.isBlank(apiKey)) {
            return List.of();
        }
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", apiKey);

        Map<String, Object> params = new HashMap<>();
        params.put("query", query.trim());
        params.put("per_page", 3);

        String response = HttpUtil.createGet(API_URL)
                .addHeaders(headers)
                .form(params)
                .execute()
                .body();

        return JSONUtil.parseObj(response)
                .getJSONArray("photos")
                .stream()
                .map(photoObj -> (JSONObject) photoObj)
                .map(photoObj -> photoObj.getJSONObject("src"))
                .map(photo -> photo.getStr("medium"))
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());
    }
}
