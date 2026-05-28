package com.xcw.aiagentbackend.service;

import com.xcw.aiagentbackend.app.MentorApp;
import com.xcw.aiagentbackend.model.chat.MentorMode;
import com.xcw.aiagentbackend.model.chat.StreamEvent;
import com.xcw.aiagentbackend.util.PlanMarkdownNormalizer;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class MentorChatService {
    private static final String MCP_TIMEOUT_HINT = "真实配图检索超时：本次图片检索超过等待时间，已先返回文字规划。可稍后重试“每个地点配图”。";
    private static final String MCP_ERROR_HINT = "真实配图检索失败：本次未获取到可靠图片，建议稍后重试或先仅生成文字版路线。";
    private static final String PLANNER_STREAM_ERROR_HINT = "## 五、复盘与下一步\n- 系统提示：规划流式输出中断，建议缩小问题范围后重试。";
    private static final Pattern BULLET_PREFIX = Pattern.compile("^[-*•]\\s*");
    private static final Pattern ORDERED_PREFIX = Pattern.compile("^\\d+[\\.)、]\\s*");
    private static final Pattern MARKDOWN_IMAGE = Pattern.compile("!\\[[^\\]]*]\\([^)]*\\)");
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[([^\\]]+)]\\([^)]*\\)");
    private static final Pattern URL = Pattern.compile("https?://\\S+");
    private static final Pattern PLACE_DESC_WITH_CN_BRACKET = Pattern.compile("^(.*?）)\\s*(.+)$");
    private static final Pattern PLACE_DESC_WITH_EN_BRACKET = Pattern.compile("^(.*?\\))\\s*(.+)$");
    private static final Pattern PLACE_HEADER = Pattern.compile("([\\p{IsHan}A-Za-z0-9·\\-\\s]{1,30}[（(][^（）()\\n]{1,20}[）)])");

    @Resource
    private MentorApp mentorApp;

    @Resource
    private PexelsImageSearchService pexelsImageSearchService;

    @Resource
    private ImageKeywordExtractor imageKeywordExtractor;

    @Value("${mentor.mcp-timeout-seconds:90}")
    private int mcpTimeoutSeconds;

    public Flux<StreamEvent> chatEventsByStream(MentorMode mode, String message, String chatId) {
        if (mode == MentorMode.PLANNER) {
            return chatPlannerWithAutoImage(message, chatId);
        }
        return chatCoachEvents(message, chatId);
    }

    private Flux<StreamEvent> chatCoachEvents(String message, String chatId) {
        boolean poiImageIntent = shouldUseMcpForPoiImage(message);
        boolean realImageSearchIntent = shouldUseRealImageSearch(message);
        if (shouldUseMcpTools(message) && !poiImageIntent && !realImageSearchIntent) {
            log.info("mentor_route route=mcp_map mode={} chatId={} messagePreview={}",
                    MentorMode.COACH, chatId, preview(message));
            return mapMcpEvents(message, chatId);
        }
        if (poiImageIntent || realImageSearchIntent) {
            log.info("mentor_route route=pexels_image mode={} reason={} chatId={} messagePreview={}",
                    MentorMode.COACH,
                    poiImageIntent ? "poi_image" : "real_image_search",
                    chatId,
                    preview(message));
            return imageSearchEvents(message, null, chatId, resolveMaxImages(message));
        }
        if (shouldUseGeneratedImageFallback(message)) {
            log.info("mentor_route route=local_image_tool mode={} chatId={} messagePreview={}",
                    MentorMode.COACH, chatId, preview(message));
            return generatedImageEvents(buildAutoImagePrompt(message), chatId);
        }
        log.info("mentor_route route=plain_stream mode={} chatId={} messagePreview={}",
                MentorMode.COACH, chatId, preview(message));
        return plainAnswerStream(mentorApp.doChatByStream(message, chatId));
    }

    private Flux<StreamEvent> chatPlannerWithAutoImage(String message, String chatId) {
        Flux<StreamEvent> thinking = Flux.just(
                streamEvent("thinking", "正在理解你的需求，准备生成可执行计划…")
        );
        Flux<StreamEvent> planStream = mentorApp.doPlannerPlanByStream(message, chatId)
                .timeout(Duration.ofSeconds(mcpTimeoutSeconds))
                .map(chunk -> streamEvent("answer", chunk))
                .onErrorResume(error -> {
                    log.warn("planner_stream_failed chatId={} reason={}", chatId, error.toString());
                    return Flux.just(streamEvent("answer", PLANNER_STREAM_ERROR_HINT));
                });

        StringBuilder planBuffer = new StringBuilder();
        Flux<StreamEvent> planWithBuffer = planStream.doOnNext(event -> {
            if (event.getContent() != null) {
                planBuffer.append(event.getContent());
            }
        });

        boolean poiImageIntent = shouldUseMcpForPoiImage(message);
        boolean realImageSearchIntent = shouldUseRealImageSearch(message);
        if (poiImageIntent || realImageSearchIntent || shouldAutoAttachPlannerPlaceImages(message)) {
            log.info("mentor_route route=planner_then_pexels mode={} reason={} chatId={} messagePreview={}",
                    MentorMode.PLANNER,
                    poiImageIntent ? "poi_image" : (realImageSearchIntent ? "real_image_search" : "planner_place_images"),
                    chatId,
                    preview(message));
            Flux<StreamEvent> imageEvents = Flux.defer(() ->
                    imageSearchEvents(message, planBuffer.toString(), chatId, resolveMaxImages(message)));
            return thinking.concatWith(planWithBuffer).concatWith(imageEvents);
        }
        if (shouldUseGeneratedImageFallback(message)) {
            Flux<StreamEvent> imageEvents = generatedImageEvents(buildAutoImagePrompt(message), chatId);
            return thinking.concatWith(planWithBuffer).concatWith(imageEvents);
        }
        return thinking.concatWith(planWithBuffer);
    }

    private Flux<StreamEvent> imageSearchEvents(String userMessage, String planText, String chatId, int maxImages) {
        return Flux.concat(
                Flux.just(streamEvent("thinking", "正在提取地点关键词并检索真实配图…")),
                Mono.fromCallable(() -> buildImageSearchEvents(userMessage, planText, maxImages))
                        .subscribeOn(Schedulers.boundedElastic())
                        .timeout(Duration.ofSeconds(mcpTimeoutSeconds))
                        .flatMapMany(Flux::fromIterable)
                        .onErrorResume(error -> {
                            log.warn("pexels_image_search_failed chatId={} reason={}", chatId, error.toString());
                            return Flux.just(
                                    streamEvent("tool_result", MCP_ERROR_HINT),
                                    streamEvent("answer", "\n\n" + MCP_ERROR_HINT)
                            );
                        })
        );
    }

    private List<StreamEvent> buildImageSearchEvents(String userMessage, String planText, int maxImages) {
        List<StreamEvent> events = new ArrayList<>();
        List<String> places = imageKeywordExtractor.extractPlaces(userMessage, planText, maxImages);
        if (places.isEmpty()) {
            events.add(streamEvent("tool_result", MCP_ERROR_HINT));
            events.add(streamEvent("answer", "\n\n" + MCP_ERROR_HINT));
            return events;
        }

        List<PexelsImageSearchService.PlaceImageResult> results =
                pexelsImageSearchService.searchForPlaces(places, maxImages);
        LinkedHashSet<String> imageUrls = new LinkedHashSet<>();
        StringBuilder answer = new StringBuilder("\n\n### 地点配图\n");
        for (PexelsImageSearchService.PlaceImageResult result : results) {
            events.add(streamEvent("tool_call", "正在检索配图", "searchImage", result.query()));
            if (result.imageUrl() != null && !result.imageUrl().isBlank()) {
                imageUrls.add(result.imageUrl());
                events.add(streamEvent("tool_result", "已检索到 " + result.placeLabel() + " 的实景图", null, null,
                        new String[]{result.imageUrl()}));
                answer.append("- ").append(result.placeLabel()).append("（实景参考）    ")
                        .append(result.description()).append("\n");
            } else {
                events.add(streamEvent("tool_result", result.placeLabel() + "：未检索到可靠图片"));
                answer.append("- ").append(result.placeLabel()).append("    未检索到可靠图片\n");
            }
        }

        StreamEvent answerEvent = streamEvent("answer", answer.toString());
        if (!imageUrls.isEmpty()) {
            answerEvent.setImages(imageUrls.toArray(String[]::new));
        }
        events.add(answerEvent);
        return events;
    }

    private Flux<StreamEvent> mapMcpEvents(String message, String chatId) {
        return Flux.concat(
                Flux.just(
                        streamEvent("thinking", "正在调用地图服务检索位置与路线…"),
                        streamEvent("tool_call", "正在调用高德地图", "amap-maps", preview(message))
                ),
                Mono.fromCallable(() -> safeDoMcpChat(message, chatId))
                        .subscribeOn(Schedulers.boundedElastic())
                        .timeout(Duration.ofSeconds(mcpTimeoutSeconds))
                        .flatMapMany(result -> Flux.fromArray(result.split("\n"))
                                .map(line -> streamEvent("answer", line + "\n")))
                        .concatWith(Flux.just(streamEvent("tool_result", "地图检索完成")))
                        .onErrorResume(error -> Flux.just(
                                streamEvent("tool_result", MCP_ERROR_HINT),
                                streamEvent("answer", MCP_ERROR_HINT)
                        ))
        );
    }

    private Flux<StreamEvent> generatedImageEvents(String prompt, String chatId) {
        return Flux.concat(
                Flux.just(
                        streamEvent("thinking", "正在调用 AI 生图工具…"),
                        streamEvent("tool_call", "正在生成配图", "generateImage", preview(prompt))
                ),
                Mono.fromCallable(() -> mentorApp.doChatWithTools(prompt, chatId))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(result -> Flux.fromArray(result.split("\n"))
                                .map(line -> streamEvent("answer", line + "\n")))
                        .concatWith(Flux.just(streamEvent("tool_result", "生图任务完成")))
        );
    }

    private Flux<StreamEvent> plainAnswerStream(Flux<String> stream) {
        return stream.map(chunk -> streamEvent("answer", chunk));
    }

    private String safeDoMcpChat(String prompt, String chatId) {
        try {
            return mentorApp.doChatWithMcp(prompt, chatId);
        } catch (Exception e) {
            log.warn("mcp_chat_failed chatId={} reason={}", chatId, e.toString());
            return MCP_ERROR_HINT;
        }
    }

    private StreamEvent streamEvent(String eventType, String content) {
        return streamEvent(eventType, content, null, null, null);
    }

    private StreamEvent streamEvent(String eventType, String content, String toolName, String toolArgs) {
        return streamEvent(eventType, content, toolName, toolArgs, null);
    }

    private StreamEvent streamEvent(String eventType, String content, String toolName, String toolArgs, String[] images) {
        return StreamEvent.builder()
                .eventType(eventType)
                .content(content == null ? "" : content)
                .toolName(toolName)
                .toolArgs(toolArgs)
                .toolResult("tool_result".equals(eventType) ? content : null)
                .images(images)
                .done(false)
                .build();
    }

    private boolean shouldUseMcpTools(String message) {
        if (message == null) {
            return false;
        }
        String text = message.toLowerCase();
        return text.contains("地图") || text.contains("路线") || text.contains("附近");
    }

    private boolean shouldUseMcpForPoiImage(String message) {
        if (message == null) {
            return false;
        }
        String text = message.toLowerCase();
        boolean hasPoiContext = text.contains("地点")
                || text.contains("景点")
                || text.contains("路线")
                || text.contains("上面每个")
                || text.contains("地方")
                || text.contains("推荐")
                || text.contains("放空");
        boolean wantsImages = wantsRealImages(message);
        return hasPoiContext && wantsImages;
    }

    private boolean shouldAutoAttachPlannerPlaceImages(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String t = message.toLowerCase();
        return t.contains("行程")
                || t.contains("攻略")
                || t.contains("约会")
                || t.contains("规划")
                || t.contains("景点")
                || t.contains("地点");
    }

    private boolean shouldUseImageTools(String message) {
        return wantsRealImages(message)
                || shouldUseGeneratedImageFallback(message);
    }

    private boolean wantsRealImages(String message) {
        if (message == null) {
            return false;
        }
        String text = message.toLowerCase();
        return text.contains("配图")
                || text.contains("图片")
                || text.contains("照片")
                || text.contains("配上")
                || text.contains("来几张")
                || text.contains("带图")
                || text.contains("有图")
                || text.contains("图文并茂");
    }

    private boolean shouldUseRealImageSearch(String message) {
        if (message == null) {
            return false;
        }
        if (!wantsRealImages(message)) {
            return false;
        }
        return !shouldUseGeneratedImageFallback(message);
    }

    private boolean shouldUseGeneratedImageFallback(String message) {
        if (message == null) {
            return false;
        }
        String text = message.toLowerCase();
        return text.contains("思维导图")
                || text.contains("脑图")
                || text.contains("流程图")
                || text.contains("示意图")
                || text.contains("概念图")
                || text.contains("海报")
                || text.contains("插画")
                || text.contains("封面图")
                || text.contains("ai生成")
                || text.contains("生成一张图");
    }

    private int resolveMaxImages(String message) {
        if (isMoreImagesRequested(message)) {
            return 12;
        }
        return 6;
    }

    private String buildAutoImagePrompt(String userMessage) {
        return """
                请基于用户需求自动生成1张配图，必须调用图片生成工具。
                要求：
                1) prompt 清晰包含场景、氛围、构图；
                2) resolution 固定为 1024:1024；
                3) 返回可访问图片地址；
                4) 如果生成失败，返回失败原因和一条可执行的替代建议。

                用户原始需求：
                """ + userMessage;
    }

    private boolean isMoreImagesRequested(String message) {
        if (message == null) {
            return false;
        }
        String text = message.toLowerCase();
        return text.contains("更多图片")
                || text.contains("多来几张")
                || text.contains("多放几张")
                || text.contains("每个地点多图")
                || text.contains("每个地方多图")
                || text.contains("多一点图片");
    }

    private String preview(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 80) {
            return normalized;
        }
        return normalized.substring(0, 80) + "...";
    }
}
