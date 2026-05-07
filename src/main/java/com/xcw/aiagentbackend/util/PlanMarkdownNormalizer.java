package com.xcw.aiagentbackend.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class PlanMarkdownNormalizer {

    private static final Pattern ORPHAN_HASH = Pattern.compile("^#{1,3}\\s*$");
    private static final Pattern EXPORT_TIP = Pattern.compile(
            "(?m)^>\\s*可点击右上角[“\"].*导出最近计划PDF.*$");
    private static final Pattern ENGLISH_PROMPT = Pattern.compile(
            "(?i)(--ar\\s+\\d|midjourney|dall[- ]?e|stable diffusion|photorealistic|softmorning|\\bv\\s*6\\.\\d)");

    private PlanMarkdownNormalizer() {
    }

    public static String normalizeForStorage(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n").trim();
        normalized = stripExportTip(normalized);
        normalized = stripOrphanHashLines(normalized);
        normalized = fixBrokenBoldMarkers(normalized);
        normalized = normalized.replaceAll("\n{3,}", "\n\n").trim();
        return normalized;
    }

    public static String normalizeForPdf(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = normalizeForStorage(text);
        normalized = stripMarkdownSyntax(normalized);
        normalized = normalized.replaceAll("\n{3,}", "\n\n").trim();
        return normalized;
    }

    public static String stripExportTip(String text) {
        if (text == null) {
            return "";
        }
        return EXPORT_TIP.matcher(text).replaceAll("").trim();
    }

    public static String stripOrphanHashLines(String text) {
        if (text == null) {
            return "";
        }
        String[] lines = text.split("\n");
        List<String> kept = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) {
                if (!kept.isEmpty() && !kept.get(kept.size() - 1).isBlank()) {
                    kept.add("");
                }
                continue;
            }
            if (ORPHAN_HASH.matcher(trimmed).matches()) {
                continue;
            }
            kept.add(line.stripTrailing());
        }
        return String.join("\n", kept).trim();
    }

    public static String fixBrokenBoldMarkers(String text) {
        if (text == null || text.isBlank()) {
            return text == null ? "" : text;
        }
        String fixed = text;
        fixed = fixed.replaceAll("\\*\\*([^*\\n]+?)\\*\\*\\s*:", "**$1**：");
        fixed = fixed.replaceAll("(?m)^-\\s*\\*\\*([^*\\n]+?)\\*\\*\\s*$", "- **$1**");
        fixed = fixed.replaceAll("(?<![*])\\*\\*(?![*])", "");
        return fixed;
    }

    public static String stripMarkdownSyntax(String text) {
        if (text == null) {
            return "";
        }
        String stripped = text;
        stripped = stripped.replaceAll("(?m)^#{1,3}\\s+", "");
        stripped = stripped.replaceAll("\\*\\*([^*]+)\\*\\*", "$1");
        stripped = stripped.replaceAll("(?m)^>\\s?", "");
        stripped = stripped.replaceAll("!\\[[^\\]]*]\\([^)]*\\)", "");
        return stripped.trim();
    }

    public static String filterEnglishPromptNoise(String text) {
        if (text == null || text.isBlank()) {
            return text == null ? "" : text;
        }
        if (ENGLISH_PROMPT.matcher(text).find()) {
            return "";
        }
        return text;
    }

    public static boolean isMarkdownHeading(String line) {
        return line != null && line.matches("^#{1,3}\\s+.+");
    }
}
