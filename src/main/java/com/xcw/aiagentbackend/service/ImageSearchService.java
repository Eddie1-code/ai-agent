package com.xcw.aiagentbackend.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 真实图片检索：通过 SearchAPI 的图片搜索引擎返回网页实拍图，
 * 用于替代 Pexels（库存图库，缺少国内小地点实景）。
 */
@Slf4j
@Service
public class ImageSearchService {

    private static final String API_URL = "https://www.searchapi.io/api/v1/search";

    private static final ExecutorService SEARCH_EXECUTOR = Executors.newFixedThreadPool(6, r -> {
        Thread t = new Thread(r, "image-search");
        t.setDaemon(true);
        return t;
    });

    @Value("${search-api.api-key:}")
    private String apiKey;

    @Value("${image-search.engine:google_images}")
    private String engine;

    public record PlaceImageResult(String placeLabel, String query, String imageUrl, String description) {
    }

    public List<PlaceImageResult> searchForPlaces(List<String> places, int maxImages) {
        if (places == null || places.isEmpty()) {
            return List.of();
        }
        int limit = Math.min(Math.max(maxImages, 1), 12);
        List<String> bounded = places.stream().limit(limit).toList();
        List<CompletableFuture<PlaceImageResult>> futures = bounded.stream()
                .map(place -> CompletableFuture.supplyAsync(() -> searchOne(place), SEARCH_EXECUTOR))
                .toList();
        List<PlaceImageResult> results = new ArrayList<>(bounded.size());
        for (CompletableFuture<PlaceImageResult> future : futures) {
            results.add(future.join());
        }
        return results;
    }

    private PlaceImageResult searchOne(String place) {
        String label = place == null ? "" : place.trim();
        if (label.isBlank()) {
            return new PlaceImageResult(label, label, null, "未检索到可靠图片，建议稍后重试。");
        }
        String imageUrl = searchImage(label);
        if (imageUrl == null) {
            return new PlaceImageResult(label, label, null, "未检索到可靠图片，建议稍后重试。");
        }
        return new PlaceImageResult(label, label, imageUrl, buildDescription(label));
    }

    public String searchImage(String query) {
        if (StrUtil.isBlank(query)) {
            return null;
        }
        if (StrUtil.isBlank(apiKey)) {
            log.warn("search_api_key_missing query={}", query);
            return null;
        }
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("engine", engine);
            params.put("q", query.trim());
            params.put("api_key", apiKey);

            String response = HttpUtil.get(API_URL, params);
            JSONObject root = JSONUtil.parseObj(response);
            JSONArray images = root.getJSONArray("images");
            if (images == null || images.isEmpty()) {
                images = root.getJSONArray("images_results");
            }
            if (images == null || images.isEmpty()) {
                log.info("image_search_empty query={} engine={}", query, engine);
                return null;
            }
            for (Object item : images) {
                if (!(item instanceof JSONObject img)) {
                    continue;
                }
                String url = extractImageUrl(img);
                if (url != null) {
                    return url;
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("image_search_failed query={} engine={} reason={}", query, engine, e.toString());
            return null;
        }
    }

    private static String extractImageUrl(JSONObject img) {
        // 前端以 124px 缩略图展示，优先取 CDN 托管的缩略图（gstatic/bing，无防盗链），
        // 原图直链常被源站防盗链拦截导致「图片无法显示」，仅作兜底。
        String thumbnail = img.getStr("thumbnail");
        if (StrUtil.isNotBlank(thumbnail)) {
            return thumbnail;
        }
        String thumbnailUrl = img.getStr("thumbnail_url");
        if (StrUtil.isNotBlank(thumbnailUrl)) {
            return thumbnailUrl;
        }
        String imageUrl = img.getStr("image_url");
        if (StrUtil.isNotBlank(imageUrl)) {
            return imageUrl;
        }
        String original = img.getStr("original");
        if (StrUtil.isNotBlank(original)) {
            return original;
        }
        JSONObject originalObj = img.getJSONObject("original");
        if (originalObj != null) {
            String link = originalObj.getStr("link");
            if (StrUtil.isNotBlank(link)) {
                return link;
            }
        }
        return null;
    }

    private String buildDescription(String label) {
        return label + " 的实景参考，适合作为行动计划的视觉锚点。";
    }
}
