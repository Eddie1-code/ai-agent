package com.xcw.aiagentbackend.service;

import cn.hutool.json.JSONUtil;
import cn.hutool.core.io.FileUtil;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Style;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.io.image.ImageDataFactory;
import com.xcw.aiagentbackend.constant.FileConstant;
import com.xcw.aiagentbackend.model.chat.ChatMessageRecord;
import com.xcw.aiagentbackend.model.chat.ExportRecord;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class PlanExportService {

    @Resource
    private ChatSessionService chatSessionService;

    public ExportRecord exportLatestPlan(String username, String sessionId) {
        ChatMessageRecord latest = chatSessionService.latestAssistantMessage(username, sessionId);
        String dir = FileConstant.FILE_SAVE_DIR + "/exports";
        FileUtil.mkdir(dir);
        String fileName = "plan-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + "-" + UUID.randomUUID().toString().substring(0, 8) + ".pdf";
        String filePath = dir + "/" + fileName;
        writePlanPdf(filePath, latest.getContent(), latest.getMetadataJson(), sessionId);
        return chatSessionService.saveExportRecord(username, sessionId, latest.getId(), filePath);
    }

    private void writePlanPdf(String filePath, String content, String metadataJson, String sessionId) {
        try (PdfWriter writer = new PdfWriter(filePath);
             PdfDocument pdf = new PdfDocument(writer);
             Document document = new Document(pdf)) {
            PdfFont font = resolvePdfFont();
            document.setFont(font);
            document.setMargins(40, 36, 40, 36);

            Style titleStyle = new Style().setFont(font).setFontSize(20);
            Style metaStyle = new Style().setFont(font).setFontSize(11).setFontColor(new DeviceRgb(92, 92, 92));
            Style h1Style = new Style().setFont(font).setFontSize(17);
            Style h2Style = new Style().setFont(font).setFontSize(14);
            Style h3Style = new Style().setFont(font).setFontSize(12);
            Style stepStyle = new Style().setFont(font).setFontSize(14);
            Style bulletStyle = new Style().setFont(font).setFontSize(11).setMarginLeft(16);
            Style orderedStyle = new Style().setFont(font).setFontSize(11).setMarginLeft(16);
            Style bodyStyle = new Style().setFont(font).setFontSize(11);

            document.add(new Paragraph("AI生活导师行动计划")
                    .addStyle(titleStyle)
                    .setMarginBottom(10));
            document.add(new Paragraph("会话ID： " + sessionId).addStyle(metaStyle).setMarginBottom(3));
            document.add(new Paragraph("导出时间： " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                    .addStyle(metaStyle)
                    .setMarginBottom(14));

            List<String> lines = normalizeStructuredText(content == null ? "" : content);
            for (String line : lines) {
                if (line.isBlank()) {
                    document.add(new Paragraph(" ").setMarginBottom(2));
                    continue;
                }
                if (line.startsWith("### ")) {
                    document.add(new Paragraph(line.substring(4)).addStyle(h3Style).setMarginTop(6).setMarginBottom(4));
                    continue;
                }
                if (line.startsWith("## ")) {
                    document.add(new Paragraph(line.substring(3)).addStyle(h2Style).setMarginTop(8).setMarginBottom(5));
                    continue;
                }
                if (line.startsWith("# ")) {
                    document.add(new Paragraph(line.substring(2)).addStyle(h1Style).setMarginTop(10).setMarginBottom(6));
                    continue;
                }
                if (isStepTitle(line)) {
                    document.add(new Paragraph(line).addStyle(stepStyle).setMarginTop(8).setMarginBottom(6));
                    continue;
                }
                if (isOrderedLine(line)) {
                    document.add(new Paragraph(line).addStyle(orderedStyle).setMarginBottom(4));
                    continue;
                }
                if (isBulletLine(line)) {
                    document.add(new Paragraph(toPdfBullet(line)).addStyle(bulletStyle).setMarginBottom(4));
                    continue;
                }
                document.add(new Paragraph(line).addStyle(bodyStyle).setMarginBottom(4));
            }

            List<String> images = extractImagesFromMetadata(metadataJson);
            if (!images.isEmpty()) {
                document.add(new Paragraph("相关配图").addStyle(h2Style).setMarginTop(10).setMarginBottom(6));
                for (String imageUrl : images) {
                    Image image = loadImageSafely(imageUrl);
                    if (image != null) {
                        image.setAutoScale(true);
                        image.setMaxHeight(320);
                        document.add(image.setMarginBottom(8));
                    } else {
                        document.add(new Paragraph("图片链接： " + imageUrl).addStyle(metaStyle).setMarginBottom(4));
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("导出 PDF 失败: " + e.getMessage(), e);
        }
    }

    private List<String> extractImagesFromMetadata(String metadataJson) {
        List<String> images = new ArrayList<>();
        if (metadataJson == null || metadataJson.isBlank()) {
            return images;
        }
        try {
            var obj = JSONUtil.parseObj(metadataJson);
            var imageArray = obj.getJSONArray("images");
            if (imageArray == null) {
                return images;
            }
            for (Object item : imageArray) {
                if (item == null) {
                    continue;
                }
                String value = String.valueOf(item).trim();
                if (!value.isBlank()) {
                    images.add(value);
                }
            }
        } catch (Exception ignored) {
            return List.of();
        }
        return images.stream().distinct().toList();
    }

    private Image loadImageSafely(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        try {
            String normalized = imageUrl.trim();
            if (normalized.startsWith("/api/public/images/")) {
                String fileName = normalized.substring("/api/public/images/".length());
                String localPath = FileConstant.FILE_SAVE_DIR + "/generated-images/" + fileName;
                if (!FileUtil.exist(localPath)) {
                    return null;
                }
                return new Image(ImageDataFactory.create(localPath));
            }
            if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
                return null;
            }
            URI uri = URI.create(normalized);
            URL url = uri.toURL();
            try (InputStream inputStream = url.openStream();
                 ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
                return new Image(ImageDataFactory.create(outputStream.toByteArray()));
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<String> normalizeStructuredText(String content) {
        String[] rawLines = content.replace("\r\n", "\n").split("\n");
        List<String> merged = new ArrayList<>();

        for (String raw : rawLines) {
            String line = raw == null ? "" : raw.trim();
            if (line.isBlank()) {
                if (!merged.isEmpty() && !merged.get(merged.size() - 1).isBlank()) {
                    merged.add("");
                }
                continue;
            }
            if (isMarkdownHeading(line) || isStepTitle(line) || isBulletLine(line) || isOrderedLine(line) || isLikelyTitle(line)) {
                merged.add(line);
                continue;
            }
            if (!merged.isEmpty()) {
                String prev = merged.get(merged.size() - 1);
                if (!prev.isBlank()
                        && !isMarkdownHeading(prev)
                        && !isStepTitle(prev)
                        && !isBulletLine(prev)
                        && !isOrderedLine(prev)
                        && !isLikelyTitle(prev)
                        && (!endsWithStrongPunctuation(prev) || line.length() <= 16 || prev.length() <= 16)) {
                    merged.set(merged.size() - 1, prev + line);
                    continue;
                }
            }
            merged.add(line);
        }
        return merged;
    }

    private boolean isStepTitle(String line) {
        return line.matches("^(?:\\d+[\\.、\\)]\\s*)?第\\s*[0-9一二三四五六七八九十百]+\\s*步[：:].*")
                || line.matches("(?i)^step\\s+\\d+\\s*:.*");
    }

    private boolean isMarkdownHeading(String line) {
        return line.startsWith("# ");
    }

    private boolean isBulletLine(String line) {
        return line.matches("^[-*•]\\s+.+");
    }

    private boolean isOrderedLine(String line) {
        return line.matches("^\\d+[\\.、\\)]\\s+.+");
    }

    private boolean isLikelyTitle(String line) {
        return line.length() <= 20 && line.matches("^.+[：:]$") && !isBulletLine(line);
    }

    private boolean endsWithStrongPunctuation(String line) {
        return line.matches("^.*[。！？!?；;：:]$");
    }

    private String toPdfBullet(String line) {
        if (line.matches("^[-*•]\\s+.+")) {
            return "• " + line.replaceFirst("^[-*•]\\s+", "");
        }
        return line;
    }

    private PdfFont resolvePdfFont() throws IOException {
        List<String> candidates = List.of(
                "C:/Windows/Fonts/msyh.ttc",
                "C:/Windows/Fonts/simsun.ttc",
                "C:/Windows/Fonts/simhei.ttf"
        );
        for (String path : candidates) {
            if (FileUtil.exist(path)) {
                String fontPath = path.endsWith(".ttc") ? path + ",0" : path;
                return PdfFontFactory.createFont(fontPath, PdfEncodings.IDENTITY_H);
            }
        }
        // 最后兜底（无中文保证），避免导出流程直接失败
        return PdfFontFactory.createFont();
    }
}
