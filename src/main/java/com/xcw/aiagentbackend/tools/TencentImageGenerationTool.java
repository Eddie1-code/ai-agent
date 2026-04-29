package com.xcw.aiagentbackend.tools;

import cn.hutool.json.JSONUtil;
import com.tencentcloudapi.aiart.v20221229.AiartClient;
import com.tencentcloudapi.aiart.v20221229.models.TextToImageLiteRequest;
import com.tencentcloudapi.aiart.v20221229.models.TextToImageLiteResponse;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.xcw.aiagentbackend.service.GeneratedImageStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class TencentImageGenerationTool {

    private final boolean enabled;
    private final String secretId;
    private final String secretKey;
    private final String region;
    private final String defaultResolution;
    private final String rspImgType;
    private final int timeoutSeconds;
    private final GeneratedImageStorageService imageStorageService;

    public TencentImageGenerationTool(
            boolean enabled,
            String secretId,
            String secretKey,
            String region,
            String defaultResolution,
            String rspImgType,
            int timeoutSeconds,
            GeneratedImageStorageService imageStorageService
    ) {
        this.enabled = enabled;
        this.secretId = secretId;
        this.secretKey = secretKey;
        this.region = region;
        this.defaultResolution = defaultResolution;
        this.rspImgType = rspImgType;
        this.timeoutSeconds = timeoutSeconds;
        this.imageStorageService = imageStorageService;
    }

    @Tool(description = "使用腾讯混元生图（极速版）根据文本生成图片，返回可访问图片地址")
    public String generateImage(
            @ToolParam(description = "图片生成提示词，推荐中文，尽量描述主体、构图、光线、风格") String prompt,
            @ToolParam(description = "反向提示词，可选；例如：模糊、低清、畸形") String negativePrompt,
            @ToolParam(description = "分辨率，可选；例如 1024:1024、1024:1536、1536:1024") String resolution,
            @ToolParam(description = "随机种子，可选；0表示随机") Integer seed
    ) {
        if (!enabled) {
            return errorPayload("腾讯生图未启用，请联系管理员开启 tencent.aiart.enabled", "Disabled");
        }
        if (prompt == null || prompt.isBlank()) {
            return errorPayload("提示词不能为空", "InvalidPrompt");
        }
        if (secretId == null || secretId.isBlank() || secretKey == null || secretKey.isBlank()) {
            return errorPayload("腾讯云密钥未配置，请设置 TENCENT_SECRET_ID / TENCENT_SECRET_KEY", "MissingCredentials");
        }
        try {
            AiartClient client = buildClient();
            TextToImageLiteRequest request = new TextToImageLiteRequest();
            request.setPrompt(prompt.trim());
            if (negativePrompt != null && !negativePrompt.isBlank()) {
                request.setNegativePrompt(negativePrompt.trim());
            }
            request.setResolution((resolution == null || resolution.isBlank()) ? defaultResolution : resolution.trim());
            request.setRspImgType(rspImgType);
            if (seed != null) {
                request.setSeed(Long.valueOf(seed));
            }

            TextToImageLiteResponse response = client.TextToImageLite(request);
            String tempImageUrl = response.getResultImage();
            String persistentUrl = tempImageUrl;
            if ("url".equalsIgnoreCase(rspImgType) && tempImageUrl != null && !tempImageUrl.isBlank()) {
                persistentUrl = imageStorageService.persistFromRemoteUrl(tempImageUrl);
            }
            Map<String, Object> payload = new HashMap<>();
            payload.put("ok", true);
            payload.put("provider", "tencent-aiart");
            payload.put("imageUrl", persistentUrl);
            payload.put("remoteImageUrl", tempImageUrl);
            payload.put("seed", response.getSeed());
            payload.put("requestId", response.getRequestId());
            payload.put("resolution", request.getResolution());
            payload.put("message", "图片生成成功");
            return JSONUtil.toJsonStr(payload);
        } catch (TencentCloudSDKException e) {
            log.warn("tencent aiart generate failed code={} msg={}", e.getErrorCode(), e.getMessage());
            return errorPayload(mapTencentError(e.getErrorCode(), e.getMessage()), e.getErrorCode());
        } catch (Exception e) {
            log.error("tencent aiart generate failed", e);
            return errorPayload("生成失败，请稍后重试", "InternalError");
        }
    }

    private AiartClient buildClient() {
        Credential credential = new Credential(secretId, secretKey);
        ClientProfile clientProfile = new ClientProfile();
        return new AiartClient(credential, region, clientProfile);
    }

    private String errorPayload(String message, String code) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("ok", false);
        payload.put("provider", "tencent-aiart");
        payload.put("message", message);
        payload.put("code", code);
        payload.put("fallbackTip", "可先使用文字方案，或缩短提示词并去掉敏感描述后重试。");
        return JSONUtil.toJsonStr(payload);
    }

    private String mapTencentError(String errorCode, String defaultMessage) {
        if (errorCode == null || errorCode.isBlank()) {
            return defaultMessage == null ? "腾讯生图调用失败" : defaultMessage;
        }
        return switch (errorCode) {
            case "RequestLimitExceeded", "RequestLimitExceeded.JobNumExceed" -> "当前生成请求过于频繁，请稍后再试";
            case "ResourceUnavailable.LowBalance", "ResourceUnavailable.InArrears" -> "腾讯云账户余额不足，暂时无法生成图片";
            case "OperationDenied.TextIllegalDetected", "FailedOperation.ModerationFailed" -> "提示词触发内容审核，请调整描述后重试";
            default -> defaultMessage == null ? ("腾讯生图调用失败：" + errorCode) : defaultMessage;
        };
    }
}
