package com.xcw.aiagentbackend.tools;

import com.xcw.aiagentbackend.service.GeneratedImageStorageService;
import jakarta.annotation.Resource;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolRegistration {

    @Value("${search-api.api-key:}")
    private String searchApiKey;
    @Value("${tencent.aiart.enabled:false}")
    private boolean tencentAiartEnabled;
    @Value("${tencent.aiart.secret-id:}")
    private String tencentSecretId;
    @Value("${tencent.aiart.secret-key:}")
    private String tencentSecretKey;
    @Value("${tencent.aiart.region:ap-guangzhou}")
    private String tencentRegion;
    @Value("${tencent.aiart.default-resolution:1024:1024}")
    private String tencentDefaultResolution;
    @Value("${tencent.aiart.rsp-img-type:url}")
    private String tencentRspImgType;
    @Value("${tencent.aiart.timeout-seconds:60}")
    private int tencentTimeoutSeconds;

    @Resource
    private GeneratedImageStorageService generatedImageStorageService;

    @Bean
    public ToolCallback[] allTools() {
        FileOperationTool fileOperationTool = new FileOperationTool();
        WebSearchTool webSearchTool = new WebSearchTool(searchApiKey);
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        ResourceDownloadTool resourceDownloadTool = new ResourceDownloadTool();
        TerminalOperationTool terminalOperationTool = new TerminalOperationTool();
        PDFGenerationTool pdfGenerationTool = new PDFGenerationTool();
        TencentImageGenerationTool tencentImageGenerationTool = new TencentImageGenerationTool(
                tencentAiartEnabled,
                tencentSecretId,
                tencentSecretKey,
                tencentRegion,
                tencentDefaultResolution,
                tencentRspImgType,
                tencentTimeoutSeconds,
                generatedImageStorageService
        );
        TerminateTool terminateTool = new TerminateTool();
        return ToolCallbacks.from(
            fileOperationTool,
            webSearchTool,
            webScrapingTool,
            resourceDownloadTool,
            terminalOperationTool,
            pdfGenerationTool,
            tencentImageGenerationTool,
            terminateTool
        );
    }
}