package com.xcw.aiagentbackend.controller;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import com.xcw.aiagentbackend.common.BaseResponse;
import com.xcw.aiagentbackend.common.ResultUtils;
import com.xcw.aiagentbackend.service.CaptchaStore;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class CaptchaController {

    @Resource
    private CaptchaStore captchaStore;

    @GetMapping("/auth/captcha")
    public BaseResponse<Map<String, String>> captcha() {
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(200, 80, 4, 20);
        String code = captcha.getCode();
        String key = captchaStore.put(code);
        String imageBase64 = captcha.getImageBase64Data();
        return ResultUtils.success(Map.of("captchaKey", key, "captchaImage", imageBase64));
    }
}
