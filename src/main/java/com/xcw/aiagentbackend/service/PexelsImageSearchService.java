package com.xcw.aiagentbackend.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PexelsImageSearchService {

    private static final String API_URL = "https://api.pexels.com/v1/search";

    @Value("${pexels.api-key:}")
    private String apiKey;

    public List<String> searchMediumImages(String query) {
        if (StrUtil.isBlank(query)) {
            return List.of();
        }
        if (StrUtil.isBlank(apiKey)) {
            log.warn("pexels_api_key_missing query={}", query);
            return List.of();
        }
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", apiKey);

            Map<String, Object> params = new HashMap<>();
            params.put("query", query.trim());
            params.put("per_page", 1);

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
        } catch (Exception e) {
            log.warn("pexels_search_failed query={} reason={}", query, e.toString());
            return List.of();
        }
    }

    public record PlaceImageResult(String placeLabel, String query, String imageUrl, String description) {
    }

    public List<PlaceImageResult> searchForPlaces(List<String> places, int maxImages) {
        List<PlaceImageResult> results = new ArrayList<>();
        if (places == null || places.isEmpty()) {
            return results;
        }
        int limit = Math.min(Math.max(maxImages, 1), 12);
        for (String place : places) {
            if (results.size() >= limit) {
                break;
            }
            String label = place == null ? "" : place.trim();
            if (label.isBlank()) {
                continue;
            }
            String query = toSearchQuery(label);
            List<String> urls = searchMediumImages(query);
            if (urls.isEmpty()) {
                results.add(new PlaceImageResult(label, query, null, "未检索到可靠图片，建议稍后重试。"));
            } else {
                results.add(new PlaceImageResult(label, query, urls.getFirst(), buildDescription(label)));
            }
        }
        return results;
    }

    private String toSearchQuery(String label) {
        String cleaned = label.replaceAll("[（(][^（）()]*[）)]", "").trim();
        if (cleaned.isBlank()) {
            cleaned = label.trim();
        }
        return cleaned + " scenic";
    }

    private String buildDescription(String label) {
        return label + " 的实景氛围参考，适合作为行动计划的视觉锚点。";
    }
}
