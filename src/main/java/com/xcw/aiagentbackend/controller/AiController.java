package com.xcw.aiagentbackend.controller;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.xcw.aiagentbackend.common.BaseResponse;
import com.xcw.aiagentbackend.common.ResultUtils;
import com.xcw.aiagentbackend.exception.BusinessException;
import com.xcw.aiagentbackend.exception.ErrorCode;
import com.xcw.aiagentbackend.model.chat.ChatStreamRequest;
import com.xcw.aiagentbackend.model.chat.MentorMode;
import com.xcw.aiagentbackend.model.chat.StreamEvent;
import com.xcw.aiagentbackend.service.ChatSessionService;
import com.xcw.aiagentbackend.service.MentorChatService;
import com.xcw.aiagentbackend.service.StreamSessionManager;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.xcw.aiagentbackend.util.PlanMarkdownNormalizer;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

@RestController
@RequestMapping("/ai")
public class AiController {
    private static final int MAX_EVENT_TEXT_LENGTH = 1200;
    private static final int MAX_TOTAL_STREAM_CHARS = 12000;
    private static final int MAX_REPEAT_CHUNK_COUNT = 6;
    private static final int DEFAULT_MAX_PREVIEW_IMAGES = 6;
    private static final int MAX_PREVIEW_IMAGES_WITH_MORE_REQUEST = 12;
    private static final int MAX_REASONABLE_IMAGE_REQUEST = 20;
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern JSON_TITLE_PATTERN = Pattern.compile("\\\\?\"title\\\\?\"\\s*:\\s*\\\\?\"(.*?)\\\\?\"");
    private static final Pattern IMAGE_URL_PATTERN = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);
    private static final Pattern LOCAL_IMAGE_PATH_PATTERN = Pattern.compile("/api/public/images/\\S+", Pattern.CASE_INSENSITIVE);
    private final Map<String, String> plannerPhaseByRequestId = new ConcurrentHashMap<>();
    private final Map<String, Integer> streamCharCountByRequestId = new ConcurrentHashMap<>();
    private final Map<String, String> lastChunkByRequestId = new ConcurrentHashMap<>();
    private final Map<String, Integer> repeatChunkCountByRequestId = new ConcurrentHashMap<>();
    private final Map<String, ArrayDeque<Long>> imageGenerateTimestampsByUser = new ConcurrentHashMap<>();

    @Value("${tencent.aiart.max-generate-per-minute:6}")
    private int maxImageGeneratePerMinute;

    @Resource
    private MentorChatService mentorChatService;

    @Resource
    private StreamSessionManager streamSessionManager;

    @Resource
    private ChatSessionService chatSessionService;

    @GetMapping(value = "/mentor/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMentorChat(String message, String chatId, String mode, String requestId, Authentication authentication) {
        ChatStreamRequest request = new ChatStreamRequest();
        request.setMessage(message);
        request.setChatId(chatId);
        request.setMode(mode);
        request.setRequestId(requestId);
        return streamMentorChatByRequest(request, authentication);
    }

    @PostMapping(value = "/mentor/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMentorChatByRequest(@RequestBody ChatStreamRequest request, Authentication authentication) {
        String reqId = (request.getRequestId() == null || request.getRequestId().isBlank())
                ? UUID.randomUUID().toString()
                : request.getRequestId();
        String chatId = (request.getChatId() == null || request.getChatId().isBlank())
                ? UUID.randomUUID().toString()
                : request.getChatId();
        request.setChatId(chatId);
        MentorMode mentorMode = MentorMode.fromValue(request.getMode());
        SseEmitter emitter = new SseEmitter(300000L);
        String username = resolveUsername(authentication);
        Integer requestedImageCount = extractRequestedImageCount(request.getMessage());
        if (requestedImageCount != null && requestedImageCount > MAX_REASONABLE_IMAGE_REQUEST) {
            sendEvent(emitter, StreamEvent.builder()
                    .requestId(reqId)
                    .seq(streamSessionManager.nextSeq(reqId))
                    .eventType("error")
                    .content("图片数量请求过大（" + requestedImageCount + " 张）。为保证质量与稳定性，单次最多支持 "
                            + MAX_REASONABLE_IMAGE_REQUEST + " 张。建议先生成 6-12 张精选图，再按地点分批补充。")
                    .done(true)
                    .build());
            emitter.complete();
            return emitter;
        }
        if (isImageGenerateRequest(request.getMessage()) && !allowImageGenerate(username)) {
            sendEvent(emitter, StreamEvent.builder()
                    .requestId(reqId)
                    .seq(streamSessionManager.nextSeq(reqId))
                    .eventType("error")
                    .content("图片生成过于频繁，请稍后再试。")
                    .done(true)
                    .build());
            emitter.complete();
            return emitter;
        }
        if (username != null) {
            try {
                chatSessionService.ensureSession(username, chatId, mentorMode.name().toLowerCase(), request.getMessage());
                chatSessionService.appendMessage(username, chatId, "user", "question", request.getMessage(), null);
            } catch (Exception ignored) {
            }
        }
        StringBuilder assistantBuffer = new StringBuilder();
        List<String> generatedImages = new ArrayList<>();
        int maxPreviewImages = resolveMaxPreviewImages(request.getMessage());

        streamSessionManager.registerSignal(reqId);
        streamCharCountByRequestId.put(reqId, 0);
        if (mentorMode == MentorMode.PLANNER) {
            plannerPhaseByRequestId.put(reqId, "answer");
        }
        sendEvent(emitter, StreamEvent.builder()
                .requestId(reqId)
                .seq(streamSessionManager.nextSeq(reqId))
                .eventType("meta")
                .content("mode=" + mentorMode.name())
                .done(false)
                .build());

        Flux<StreamEvent> stream = mentorChatService.chatEventsByStream(mentorMode, request.getMessage(), chatId);
        streamSessionManager.setSubscription(reqId, stream.subscribe(event -> {
            if (!streamSessionManager.isCancelled(reqId)) {
                String eventType = event.getEventType() == null ? "answer" : event.getEventType();
                String rawChunk = event.getContent();
                String displayChunk = sanitizeChunkForDisplay(mentorMode, eventType, rawChunk);
                displayChunk = PlanMarkdownNormalizer.filterEnglishPromptNoise(displayChunk);
                if ("answer".equals(eventType) && displayChunk != null && !displayChunk.isBlank()) {
                    assistantBuffer.append(displayChunk);
                }
                String toolName = event.getToolName();
                String toolArgs = event.getToolArgs();
                String toolResult = event.getToolResult();
                String[] eventImages = event.getImages();
                String[] images = limitImagesForRequest(
                        mergeImageCandidates(eventImages, extractImageUrls(rawChunk)),
                        generatedImages,
                        maxPreviewImages
                );
                if (images != null && images.length > 0) {
                    for (String image : images) {
                        if (image != null && !image.isBlank()) {
                            generatedImages.add(image);
                        }
                    }
                }
                if ("tool_call".equals(eventType) && toolName != null) {
                    toolArgs = truncateText(toolArgs, 320);
                } else if ("tool_result".equals(eventType)) {
                    toolResult = displayChunk;
                }
                if (shouldForceStop(reqId, displayChunk)) {
                    sendEvent(emitter, StreamEvent.builder()
                            .requestId(reqId)
                            .seq(streamSessionManager.nextSeq(reqId))
                            .eventType("error")
                            .content("本次规划输出过长或重复过多，已自动停止。请缩小问题范围后重试。")
                            .done(false)
                            .build());
                    streamSessionManager.cancel(reqId);
                    return;
                }
                sendEvent(emitter, StreamEvent.builder()
                        .requestId(reqId)
                        .seq(streamSessionManager.nextSeq(reqId))
                        .eventType(eventType)
                        .content(displayChunk)
                        .toolName(toolName)
                        .toolArgs(toolArgs)
                        .toolResult(toolResult)
                        .images(images)
                        .done(false)
                        .build());
            }
        }, error -> {
            sendEvent(emitter, StreamEvent.builder()
                    .requestId(reqId)
                    .seq(streamSessionManager.nextSeq(reqId))
                    .eventType("error")
                    .content(error.getMessage())
                    .done(true)
                    .build());
            streamSessionManager.cleanup(reqId);
            cleanupPlannerState(reqId);
            // SSE 场景下已通过 error 事件把失败告知前端，避免再抛给 MVC 触发二次异常序列化。
            emitter.complete();
        }, () -> {
            boolean cancelled = streamSessionManager.isCancelled(reqId);
            if (username != null && assistantBuffer.length() > 0) {
                try {
                    String normalizedAnswer = PlanMarkdownNormalizer.normalizeForStorage(assistantBuffer.toString().trim());
                    chatSessionService.appendMessage(
                            username,
                            chatId,
                            "assistant",
                            cancelled ? "cancelled" : "answer",
                            normalizedAnswer,
                            buildAssistantMetadataJson(generatedImages)
                    );
                } catch (Exception ignored) {
                }
            }
            sendEvent(emitter, StreamEvent.builder()
                    .requestId(reqId)
                    .seq(streamSessionManager.nextSeq(reqId))
                    .eventType(cancelled ? "cancelled" : "done")
                    .content(cancelled ? "已停止" : "")
                    .done(true)
                    .build());
            streamSessionManager.cleanup(reqId);
            cleanupPlannerState(reqId);
            emitter.complete();
        }));

        emitter.onTimeout(() -> {
            streamSessionManager.cancel(reqId);
            streamSessionManager.cleanup(reqId);
            cleanupPlannerState(reqId);
            emitter.complete();
        });
        emitter.onCompletion(() -> {
            streamSessionManager.cleanup(reqId);
            cleanupPlannerState(reqId);
        });
        return emitter;
    }

    @PostMapping("/mentor/chat/stop")
    public BaseResponse<Boolean> stopMentorChat(@RequestBody ChatStreamRequest request) {
        if (request.getRequestId() == null || request.getRequestId().isBlank()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "requestId 不能为空");
        }
        if (!streamSessionManager.hasSession(request.getRequestId())) {
            return ResultUtils.success(false);
        }
        boolean stopped = streamSessionManager.cancel(request.getRequestId());
        return ResultUtils.success(stopped);
    }

    @GetMapping(value = "/manus/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter doChatWithManus(String message, Authentication authentication) {
        return streamMentorChat(message, null, "planner", null, authentication);
    }

    private void sendEvent(SseEmitter emitter, StreamEvent event) {
        try {
            emitter.send(event);
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private String resolveEventType(MentorMode mode, String requestId, String chunk) {
        String text = chunk == null ? "" : chunk.trim();
        if (text.contains("工具名称：") || text.toLowerCase().contains("toolcall")) {
            return "tool_call";
        }
        if (text.contains("完成了它的任务") || text.toLowerCase().contains("result")) {
            return "tool_result";
        }
        if (mode != MentorMode.PLANNER) {
            return "answer";
        }
        if (text.contains("【行动计划】") || text.contains("行动计划")) {
            plannerPhaseByRequestId.put(requestId, "answer");
            return "answer";
        }
        if (text.contains("【复盘与下一步】")) {
            plannerPhaseByRequestId.put(requestId, "answer");
            return "answer";
        }
        if (text.startsWith("# ") || text.startsWith("## ") || text.startsWith("### ")) {
            plannerPhaseByRequestId.put(requestId, "answer");
            return "answer";
        }
        if (text.contains("## 一、目标理解")
                || text.contains("## 二、约束与风险")
                || text.contains("## 三、计划策略")
                || text.contains("## 四、具体方案")
                || text.contains("## 五、复盘与下一步")) {
            plannerPhaseByRequestId.put(requestId, "answer");
            return "answer";
        }
        if (text.contains("【思考框架】")) {
            plannerPhaseByRequestId.put(requestId, "thinking");
            return "thinking";
        }
        if (text.contains("思考过程")) {
            plannerPhaseByRequestId.put(requestId, "thinking");
            return "thinking";
        }
        return plannerPhaseByRequestId.getOrDefault(requestId, "answer");
    }

    private String extractToolName(String chunk) {
        if (chunk == null) {
            return null;
        }
        String marker = "工具名称：";
        int start = chunk.indexOf(marker);
        if (start < 0) {
            return null;
        }
        String remain = chunk.substring(start + marker.length()).trim();
        int commaIndex = remain.indexOf("，参数：");
        if (commaIndex > 0) {
            return remain.substring(0, commaIndex).trim();
        }
        return remain;
    }

    private String extractToolArgs(String chunk) {
        if (chunk == null) {
            return null;
        }
        String marker = "，参数：";
        int start = chunk.indexOf(marker);
        if (start < 0) {
            return null;
        }
        return chunk.substring(start + marker.length()).trim();
    }

    private String resolveUsername(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        String principal = String.valueOf(authentication.getPrincipal());
        if (principal.isBlank() || principal.startsWith("apiKey:")) {
            return null;
        }
        return principal;
    }

    private String sanitizeChunkForDisplay(MentorMode mode, String eventType, String chunk) {
        if (chunk == null) {
            return "";
        }
        String text = normalizeEscapedText(chunk);
        text = summarizeTencentImagePayload(text);
        if (mode == MentorMode.PLANNER && ("thinking".equals(eventType) || "tool_result".equals(eventType))) {
            if (looksLikeHtml(text)) {
                String plain = stripHtml(text);
                if (plain.isBlank()) {
                    text = "[工具返回网页源码，已隐藏]";
                } else {
                    text = "[工具返回网页源码，已自动提取摘要]\n" + truncateText(plain, 260);
                }
            } else if (looksLikeStructuredJson(text)) {
                text = summarizeStructuredJson(text);
            }
        }
        text = replacePotentialBrokenImageLinks(text);
        text = stripImageLinkArtifacts(text);
        text = stripNoisySymbolLines(text);
        text = PlanMarkdownNormalizer.stripOrphanHashLines(text);
        text = foldLongImageLinks(text);
        return truncateText(text, MAX_EVENT_TEXT_LENGTH);
    }

    private String summarizeTencentImagePayload(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return text;
        }
        try {
            JSONObject json = JSONUtil.parseObj(trimmed);
            if (!"tencent-aiart".equals(json.getStr("provider"))) {
                return text;
            }
            boolean ok = json.getBool("ok", false);
            if (ok) {
                String resolution = json.getStr("resolution");
                return "图片生成成功（见下方预览）\n- 分辨率：" + (resolution == null ? "默认" : resolution);
            }
            String message = json.getStr("message");
            String fallbackTip = json.getStr("fallbackTip");
            return "图片生成失败：" + (message == null ? "请稍后重试" : message) +
                    (fallbackTip == null ? "" : "\n建议：" + fallbackTip);
        } catch (Exception ignored) {
            return text;
        }
    }

    private String replacePotentialBrokenImageLinks(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        if (text.contains("\"provider\":\"tencent-aiart\"")) {
            return text;
        }
        Matcher matcher = IMAGE_URL_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        boolean replaced = false;
        while (matcher.find()) {
            String url = matcher.group();
            String lower = url.toLowerCase();
            if (lower.contains("myqcloud.com")) {
                continue;
            }
            boolean looksLikeImage = lower.contains("imgur.com")
                    || lower.endsWith(".png")
                    || lower.endsWith(".jpg")
                    || lower.endsWith(".jpeg")
                    || lower.endsWith(".webp")
                    || lower.endsWith(".gif");
            if (looksLikeImage) {
                matcher.appendReplacement(sb, "【图片直链暂不可用，请改为文字描述或本地上传图片】");
                replaced = true;
            }
        }
        matcher.appendTail(sb);
        return replaced ? sb.toString() : text;
    }

    private String foldLongImageLinks(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        // 保留结构化 JSON（供图片提取）不做替换，避免影响解析。
        String trimmed = text.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return text;
        }
        Matcher matcher = IMAGE_URL_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        boolean replaced = false;
        while (matcher.find()) {
            String url = matcher.group();
            String lower = url.toLowerCase();
            boolean isLikelyImageUrl = lower.contains("myqcloud.com")
                    || lower.contains("imgur.com")
                    || lower.endsWith(".png")
                    || lower.endsWith(".jpg")
                    || lower.endsWith(".jpeg")
                    || lower.endsWith(".webp")
                    || lower.endsWith(".gif");
            boolean isLongOrSigned = url.length() > 90 || lower.contains("q-signature=") || lower.contains("x-amz-signature=");
            if (isLikelyImageUrl && isLongOrSigned) {
                matcher.appendReplacement(sb, "");
                replaced = true;
                continue;
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(url));
        }
        matcher.appendTail(sb);
        return replaced ? sb.toString().replaceAll("[ \\t]{2,}", " ").replaceAll("\\n{3,}", "\n\n").trim() : text;
    }

    private String stripImageLinkArtifacts(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String cleaned = text;
        // 移除 Markdown 图片语法，避免正文出现链接占位。
        cleaned = cleaned.replaceAll("!\\[[^\\]]*]\\((https?://\\S+)\\)", "");
        cleaned = cleaned.replaceAll("!\\[[^\\]]*]\\([^)]*\\)", "");
        cleaned = cleaned.replace("![]", "");
        // 移除独立的纯 URL 行（图片链接会由下方图片卡片展示）。
        cleaned = cleaned.replaceAll("(?m)^\\s*https?://\\S+\\s*$", "");
        cleaned = cleaned.replace("[图片链接已折叠，见下方预览]", "");
        cleaned = cleaned.replace("【图片直链暂不可用，请改为文字描述或本地上传图片】", "");
        return cleaned.replaceAll("[ \\t]{2,}", " ").replaceAll("\\n{3,}", "\n\n").trim();
    }

    private String stripNoisySymbolLines(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String cleaned = text;
        // 仅移除 Markdown 图片残片，保留地点文案中的 emoji/符号样式（例如📍）。
        cleaned = cleaned.replaceAll("!\\[[^\\]]*]\\(", "");
        cleaned = cleaned.replaceAll("!\\[\\]", "");
        String[] lines = cleaned.split("\\R");
        List<String> kept = new ArrayList<>();
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String normalized = line.trim();
            if (normalized.isBlank()) {
                continue;
            }
            if (normalized.matches("^[!\\[\\]\\(\\){}<>_—~.,:;，。！？、：；\\-\\s]+$")) {
                continue;
            }
            kept.add(normalized.replaceAll("[ \\t]{2,}", " "));
        }
        return String.join("\n", kept);
    }

    private String[] extractImageUrls(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        List<String> imageUrls = new ArrayList<>();
        try {
            String trimmed = text.trim();
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                JSONObject jsonObject = JSONUtil.parseObj(text);
                String provider = jsonObject.getStr("provider");
                String imageUrl = jsonObject.getStr("imageUrl");
                if ("tencent-aiart".equals(provider)) {
                    String normalizedImageUrl = normalizeImageUrl(imageUrl);
                    if (normalizedImageUrl != null && !normalizedImageUrl.isBlank()) {
                        imageUrls.add(normalizedImageUrl);
                    }
                    String remoteImageUrl = normalizeImageUrl(jsonObject.getStr("remoteImageUrl"));
                    if (remoteImageUrl != null && !remoteImageUrl.isBlank()) {
                        imageUrls.add(remoteImageUrl);
                    }
                }
            }
        } catch (Exception ignored) {
        }

        Matcher urlMatcher = IMAGE_URL_PATTERN.matcher(text);
        while (urlMatcher.find()) {
            String url = normalizeImageUrl(urlMatcher.group());
            if (url != null && isLikelyImageUrl(url)) {
                imageUrls.add(url);
            }
        }

        // 无论是否已有远端链接，都额外提取本地持久化链接并在排序时优先展示
        Matcher localMatcher = LOCAL_IMAGE_PATH_PATTERN.matcher(text);
        while (localMatcher.find()) {
            String url = normalizeImageUrl(localMatcher.group());
            if (url != null) {
                imageUrls.add(url);
            }
        }

        // 优先使用本地持久化地址，避免 COS 临时链接失效导致无法预览
        imageUrls = imageUrls.stream()
                .filter(item -> item != null && !item.isBlank())
                .distinct()
                .sorted((a, b) -> {
                    boolean aLocal = a.startsWith("/api/public/images/");
                    boolean bLocal = b.startsWith("/api/public/images/");
                    if (aLocal == bLocal) return 0;
                    return aLocal ? -1 : 1;
                })
                .toList();

        if (imageUrls.isEmpty()) {
            return null;
        }
        return imageUrls.toArray(String[]::new);
    }

    private String[] limitImagesForRequest(String[] candidateImages, List<String> generatedImages, int maxPreviewImages) {
        if (candidateImages == null || candidateImages.length == 0) {
            return null;
        }
        if (generatedImages == null) {
            generatedImages = new ArrayList<>();
        }
        int remaining = Math.max(0, maxPreviewImages - generatedImages.size());
        if (remaining <= 0) {
            return null;
        }
        List<String> accepted = new ArrayList<>();
        for (String image : candidateImages) {
            if (image == null || image.isBlank()) {
                continue;
            }
            if (generatedImages.contains(image) || accepted.contains(image)) {
                continue;
            }
            accepted.add(image);
            if (accepted.size() >= remaining) {
                break;
            }
        }
        if (accepted.isEmpty()) {
            return null;
        }
        return accepted.toArray(String[]::new);
    }

    private int resolveMaxPreviewImages(String message) {
        if (message == null) {
            return DEFAULT_MAX_PREVIEW_IMAGES;
        }
        String text = message.toLowerCase();
        boolean moreImagesRequested = text.contains("更多图片")
                || text.contains("多来几张")
                || text.contains("多放几张")
                || text.contains("每个地点多图")
                || text.contains("每个地方多图")
                || text.contains("多一点图片");
        return moreImagesRequested ? MAX_PREVIEW_IMAGES_WITH_MORE_REQUEST : DEFAULT_MAX_PREVIEW_IMAGES;
    }

    private Integer extractRequestedImageCount(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        Matcher matcher = Pattern.compile("(\\d{1,4})\\s*张").matcher(message);
        Integer maxCount = null;
        while (matcher.find()) {
            try {
                int count = Integer.parseInt(matcher.group(1));
                if (maxCount == null || count > maxCount) {
                    maxCount = count;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return maxCount;
    }

    private boolean isLikelyImageUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String lower = url.toLowerCase();
        if (lower.contains("myqcloud.com")) {
            return true;
        }
        if (lower.contains("pexels.com") || lower.contains("unsplash.com") || lower.contains("pixabay.com")) {
            return true;
        }
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".webp")
                || lower.endsWith(".gif")
                || lower.contains(".png?")
                || lower.contains(".jpg?")
                || lower.contains(".jpeg?")
                || lower.contains(".webp?")
                || lower.contains(".gif?");
    }

    private String normalizeImageUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return null;
        }
        String normalized = rawUrl.trim();
        // 去掉 Markdown 或标点带来的收尾噪音
        normalized = normalized.replaceAll("[),\\]}>\"']+$", "");
        // 如果误拼接了第二个 http 链接，只保留第一个
        int secondHttp = normalized.indexOf("http", 8);
        if (secondHttp > 0) {
            normalized = normalized.substring(0, secondHttp);
            normalized = normalized.replaceAll("[),\\]}>\"']+$", "");
        }
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://") && !normalized.startsWith("/api/")) {
            return null;
        }
        return normalized;
    }

    private boolean looksLikeHtml(String text) {
        String lower = text.toLowerCase();
        return lower.contains("<!doctype") || lower.contains("<html") || lower.contains("<script") || lower.contains("<body") || lower.contains("<div");
    }

    private boolean looksLikeStructuredJson(String text) {
        String t = text.trim();
        return t.startsWith("{") || t.startsWith("[") || t.contains("\"title\"") || t.contains("\\\"title\\\"");
    }

    private String stripHtml(String text) {
        String noScript = text.replaceAll("(?is)<script.*?>.*?</script>", " ");
        String noStyle = noScript.replaceAll("(?is)<style.*?>.*?</style>", " ");
        String noTags = HTML_TAG_PATTERN.matcher(noStyle).replaceAll(" ");
        return noTags.replaceAll("\\s+", " ").trim();
    }

    private String truncateText(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "\n...[内容过长已截断]";
    }

    private String normalizeEscapedText(String text) {
        return text
                .replace("\\u003c", "<")
                .replace("\\u003e", ">")
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\/", "/");
    }

    private String summarizeStructuredJson(String text) {
        List<String> titles = new ArrayList<>();
        Matcher matcher = JSON_TITLE_PATTERN.matcher(text);
        while (matcher.find() && titles.size() < 3) {
            String title = matcher.group(1);
            if (title != null && !title.isBlank()) {
                titles.add(title.trim());
            }
        }
        if (titles.isEmpty()) {
            return "[工具返回结构化数据，已隐藏原始内容]";
        }
        StringBuilder sb = new StringBuilder("[工具返回结构化数据，已提取重点]\n");
        for (int i = 0; i < titles.size(); i++) {
            sb.append(i + 1).append(". ").append(titles.get(i)).append("\n");
        }
        return sb.toString().trim();
    }

    private boolean shouldForceStop(String requestId, String chunk) {
        if (chunk == null || chunk.isBlank()) {
            return false;
        }
        int total = streamCharCountByRequestId.getOrDefault(requestId, 0) + chunk.length();
        streamCharCountByRequestId.put(requestId, total);
        if (total > MAX_TOTAL_STREAM_CHARS) {
            return true;
        }
        String normalized = chunk.trim();
        String last = lastChunkByRequestId.get(requestId);
        if (normalized.equals(last)) {
            int repeat = repeatChunkCountByRequestId.getOrDefault(requestId, 1) + 1;
            repeatChunkCountByRequestId.put(requestId, repeat);
            return repeat >= MAX_REPEAT_CHUNK_COUNT;
        }
        lastChunkByRequestId.put(requestId, normalized);
        repeatChunkCountByRequestId.put(requestId, 1);
        return false;
    }

    private void cleanupPlannerState(String requestId) {
        plannerPhaseByRequestId.remove(requestId);
        streamCharCountByRequestId.remove(requestId);
        lastChunkByRequestId.remove(requestId);
        repeatChunkCountByRequestId.remove(requestId);
    }

    private String[] mergeImageCandidates(String[] primary, String[] secondary) {
        List<String> merged = new ArrayList<>();
        if (primary != null) {
            for (String item : primary) {
                if (item != null && !item.isBlank()) {
                    merged.add(item);
                }
            }
        }
        if (secondary != null) {
            for (String item : secondary) {
                if (item != null && !item.isBlank() && !merged.contains(item)) {
                    merged.add(item);
                }
            }
        }
        if (merged.isEmpty()) {
            return null;
        }
        return merged.toArray(String[]::new);
    }

    private String buildAssistantMetadataJson(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        List<String> normalized = images.stream()
                .filter(item -> item != null && !item.isBlank())
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            return null;
        }
        String provider = normalized.stream().anyMatch(url -> url.toLowerCase().contains("pexels.com"))
                ? "pexels"
                : "tencent-aiart";
        return JSONUtil.toJsonStr(Map.of(
                "provider", provider,
                "images", normalized
        ));
    }

    private boolean isImageGenerateRequest(String message) {
        if (message == null) {
            return false;
        }
        String text = message.toLowerCase();
        return text.contains("生成图片")
                || text.contains("生图")
                || text.contains("配图")
                || text.contains("海报")
                || text.contains("封面图")
                || text.contains("插画")
                || text.contains("宣传图");
    }

    private boolean allowImageGenerate(String username) {
        String bucketKey = (username == null || username.isBlank()) ? "guest" : username;
        long now = System.currentTimeMillis();
        long minTs = now - 60_000L;
        ArrayDeque<Long> deque = imageGenerateTimestampsByUser.computeIfAbsent(bucketKey, key -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst() < minTs) {
                deque.pollFirst();
            }
            if (deque.size() >= maxImageGeneratePerMinute) {
                return false;
            }
            deque.addLast(now);
            return true;
        }
    }
}