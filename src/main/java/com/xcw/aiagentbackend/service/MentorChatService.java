package com.xcw.aiagentbackend.service;

import com.xcw.aiagentbackend.app.MentorApp;
import com.xcw.aiagentbackend.model.chat.MentorMode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class MentorChatService {
    private static final String EXPORT_TIP = "\n\n> 可点击右上角“导出最近计划PDF”一键导出本次计划。";
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

    public Flux<String> chatByStream(MentorMode mode, String message, String chatId) {
        if (mode == MentorMode.PLANNER) {
            return chatPlannerWithAutoImage(message, chatId);
        }
        boolean poiImageIntent = shouldUseMcpForPoiImage(message);
        boolean realImageSearchIntent = shouldUseRealImageSearchByDefault(message);
        if (shouldUseImageTools(message) && (poiImageIntent || realImageSearchIntent)) {
            log.info("mentor_route guard=block_local_image_tool mode={} chatId={} messagePreview={}",
                    mode, chatId, preview(message));
        }
        if (shouldUseMcpTools(message) || poiImageIntent || realImageSearchIntent) {
            log.info("mentor_route route=mcp mode={} reason={} chatId={} messagePreview={}",
                    mode,
                    poiImageIntent ? "poi_image" : (realImageSearchIntent ? "real_image_search" : "map_query"),
                    chatId,
                    preview(message));
            String mcpPrompt = (poiImageIntent || realImageSearchIntent) ? buildPoiImageSearchPrompt(message) : message;
            String result = mentorApp.doChatWithMcp(mcpPrompt, chatId);
            String normalized = normalizePoiListOutput(result, poiImageIntent || realImageSearchIntent);
            return Flux.fromArray(normalized.split("\n"));
        }
        if (shouldUseGeneratedImageFallback(message)) {
            log.info("mentor_route route=local_image_tool mode={} reason=image_request chatId={} messagePreview={}",
                    mode, chatId, preview(message));
            String result = mentorApp.doChatWithTools(message, chatId);
            return Flux.fromArray(result.split("\n"));
        }
        log.info("mentor_route route=plain_stream mode={} chatId={} messagePreview={}", mode, chatId, preview(message));
        return mentorApp.doChatByStream(message, chatId);
    }

    private Flux<String> chatPlannerWithAutoImage(String message, String chatId) {
        Flux<String> planStream = mentorApp.doPlannerPlanByStream(message, chatId);
        boolean poiImageIntent = shouldUseMcpForPoiImage(message);
        boolean realImageSearchIntent = shouldUseRealImageSearchByDefault(message);
        if (shouldUseImageTools(message) && (poiImageIntent || realImageSearchIntent)) {
            log.info("mentor_route guard=block_planner_local_image_tool mode={} chatId={} messagePreview={}",
                    MentorMode.PLANNER, chatId, preview(message));
        }
        if (poiImageIntent || realImageSearchIntent) {
            log.info("mentor_route route=planner_then_mcp mode={} reason=poi_image chatId={} messagePreview={}",
                    MentorMode.PLANNER, chatId, preview(message));
            String poiImagePrompt = buildPoiImageSearchPrompt(message);
            Mono<String> poiImageResult = Mono.fromSupplier(() -> mentorApp.doChatWithMcp(poiImagePrompt, chatId));
            return appendExportTip(planStream.concatWith(
                    poiImageResult.flatMapMany(result -> Flux.fromArray(normalizePoiListOutput(result, true).split("\n")))
            ));
        }
        if (!shouldUseGeneratedImageFallback(message)) {
            log.info("mentor_route route=planner_only mode={} chatId={} messagePreview={}",
                    MentorMode.PLANNER, chatId, preview(message));
            return appendExportTip(planStream);
        }
        log.info("mentor_route route=planner_then_local_image_tool mode={} reason=image_request chatId={} messagePreview={}",
                MentorMode.PLANNER, chatId, preview(message));
        String imagePrompt = buildAutoImagePrompt(message);
        Mono<String> imageResult = Mono.fromSupplier(() -> mentorApp.doChatWithTools(imagePrompt, chatId));
        return appendExportTip(planStream.concatWith(imageResult.flatMapMany(result -> Flux.fromArray(result.split("\n")))));
    }

    private boolean shouldUseMcpTools(String message) {
        if (message == null) {
            return false;
        }
        String text = message.toLowerCase();
        return text.contains("地图")
                || text.contains("路线")
                || text.contains("附近");
    }

    /**
     * 地点类配图需求优先走 MCP（高德位置 + 图片搜索），避免走本地通用生图工具。
     */
    private boolean shouldUseMcpForPoiImage(String message) {
        if (message == null) {
            return false;
        }
        String text = message.toLowerCase();
        boolean hasPoiContext = text.contains("地点")
                || text.contains("景点")
                || text.contains("路线")
                || text.contains("上面每个");
        boolean wantsImages = text.contains("配图")
                || text.contains("图片")
                || text.contains("照片");
        return hasPoiContext && wantsImages;
    }

    private boolean shouldUseImageTools(String message) {
        if (message == null) {
            return false;
        }
        String text = message.toLowerCase();
        return text.contains("配图")
                || text.contains("封面图")
                || text.contains("海报")
                || text.contains("插画")
                || text.contains("生成图片")
                || text.contains("图文并茂");
    }

    /**
     * 你的产品策略：大多数图片需求优先真实图检索（SearchAPI/MCP）。
     */
    private boolean shouldUseRealImageSearchByDefault(String message) {
        if (message == null) {
            return false;
        }
        String text = message.toLowerCase();
        boolean asksImage = shouldUseImageTools(message);
        if (!asksImage) {
            return false;
        }
        // 明确要求创意生图时，不走默认真实图检索。
        return !shouldUseGeneratedImageFallback(message);
    }

    /**
     * 仅在用户明确要求“创意生成/示意图”时，才启用腾讯生图兜底。
     */
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

    private String buildPoiImageSearchPrompt(String userMessage) {
        return """
                你必须优先调用 MCP 能力完成“真实地点配图”，禁止使用 AI 生图工具替代真实照片。
                要求：
                1) 先做地点确认：如需地理位置可调用高德 MCP；
                2) 图片来源必须优先调用 image-search-mcp-server 的 searchImage（SearchAPI 真实图片）；
                3) 文字输出必须是无序列表（每个地点单独一行），格式严格为：
                   - 地点名（场景标签）    简短描述（20~40字）
                   示例：
                   - 金光华广场（西门夜景）    罗湖核心商圈地标，暖光玻璃幕墙+地面灯带，适合初见合影与轻快起步。
                4) 不要在正文里输出任何 URL、Markdown 链接或“链接折叠”提示；
                5) 图片链接可以在工具调用结果中返回给系统用于下方图片卡片渲染，但不要写进正文文案；
                6) 如果某地点暂时检索不到图片，明确说明“未检索到可靠图片”，不要编造链接。
                7) 不要输出工具原始调试文本。
                8) 图片数量控制：默认每个地点最多1张，总图片数不超过地点数，且最多6张；只有用户明确提出“更多图片/多来几张/每个地点多图”时，才可适度增加到最多12张。

                用户原始需求：
                """ + userMessage;
    }

    private boolean isMoreImagesRequested(String message) {
        String text = message.toLowerCase();
        return text.contains("更多图片")
                || text.contains("多来几张")
                || text.contains("多放几张")
                || text.contains("每个地点多图")
                || text.contains("每个地方多图")
                || text.contains("多一点图片");
    }

    private String normalizePoiListOutput(String text, boolean forceListFormat) {
        if (text == null || text.isBlank() || !forceListFormat) {
            return text;
        }

        String cleaned = cleanForPoiFormatting(text);
        if (cleaned.isEmpty()) {
            return text;
        }

        List<String> extractedByHeader = extractEntriesByPlaceHeader(cleaned);
        if (!extractedByHeader.isEmpty()) {
            return String.join("\n", extractedByHeader);
        }

        String[] lines = cleaned.split("\\R");
        List<String> normalizedLines = new ArrayList<>();
        for (String rawLine : lines) {
            String line = sanitizeText(rawLine);
            if (line.isEmpty()) {
                continue;
            }
            line = BULLET_PREFIX.matcher(line).replaceFirst("");
            line = ORDERED_PREFIX.matcher(line).replaceFirst("");
            line = line.replaceAll("\\s+", " ").trim();
            if (line.isEmpty()) {
                continue;
            }
            Matcher cnBracketMatcher = PLACE_DESC_WITH_CN_BRACKET.matcher(line);
            if (cnBracketMatcher.matches()) {
                line = cnBracketMatcher.group(1).trim() + " " + cnBracketMatcher.group(2).trim();
            } else {
                Matcher enBracketMatcher = PLACE_DESC_WITH_EN_BRACKET.matcher(line);
                if (enBracketMatcher.matches()) {
                    line = enBracketMatcher.group(1).trim() + " " + enBracketMatcher.group(2).trim();
                }
            }
            normalizedLines.add("- " + line);
        }
        return normalizedLines.isEmpty() ? text : String.join("\n", normalizedLines);
    }

    private List<String> extractEntriesByPlaceHeader(String cleaned) {
        Matcher matcher = PLACE_HEADER.matcher(cleaned);
        List<Integer> starts = new ArrayList<>();
        List<Integer> ends = new ArrayList<>();
        List<String> headers = new ArrayList<>();
        while (matcher.find()) {
            String header = sanitizeText(matcher.group(1));
            if (header.isEmpty()) {
                continue;
            }
            starts.add(matcher.start());
            ends.add(matcher.end());
            headers.add(header);
        }
        if (headers.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (int i = 0; i < headers.size(); i++) {
            int descStart = ends.get(i);
            int descEnd = (i + 1 < headers.size()) ? starts.get(i + 1) : cleaned.length();
            if (descStart > descEnd) {
                continue;
            }
            String desc = sanitizeText(cleaned.substring(descStart, descEnd));
            if (desc.isEmpty()) {
                continue;
            }
            result.add("- " + headers.get(i) + " " + desc);
        }
        return result;
    }

    private String cleanForPoiFormatting(String text) {
        String cleaned = text;
        cleaned = MARKDOWN_IMAGE.matcher(cleaned).replaceAll(" ");
        cleaned = MARKDOWN_LINK.matcher(cleaned).replaceAll("$1");
        cleaned = URL.matcher(cleaned).replaceAll(" ");
        cleaned = cleaned.replaceAll("[•●◦▪▫◆◇■□★☆※→←↔↑↓]+", " ");
        cleaned = cleaned.replaceAll("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]", " ");
        cleaned = cleaned.replaceAll("\\s*\\n\\s*", "\n");
        cleaned = cleaned.replaceAll("[ \\t]+", " ");
        return sanitizeText(cleaned);
    }

    private String sanitizeText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isLetterOrDigit(ch)
                    || Character.isWhitespace(ch)
                    || Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN
                    || "（）()，。！？、：；《》“”\"'·+-/&".indexOf(ch) >= 0) {
                builder.append(ch);
            }
        }
        String normalized = builder.toString().replaceAll("\\s+", " ").trim();
        return normalized.replaceAll("^[-*•\\s]+", "").trim();
    }

    private Flux<String> appendExportTip(Flux<String> stream) {
        return stream.concatWithValues(EXPORT_TIP);
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
