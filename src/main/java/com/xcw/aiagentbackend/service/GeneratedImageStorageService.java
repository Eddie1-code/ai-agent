package com.xcw.aiagentbackend.service;

import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpUtil;
import com.xcw.aiagentbackend.constant.FileConstant;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.UUID;

@Service
public class GeneratedImageStorageService {

    private static final String GENERATED_IMAGE_DIR = FileConstant.FILE_SAVE_DIR + "/generated-images";

    public String persistFromRemoteUrl(String remoteUrl) {
        FileUtil.mkdir(GENERATED_IMAGE_DIR);
        String suffix = resolveSuffix(remoteUrl);
        String fileName = "gen-" + UUID.randomUUID().toString().replace("-", "") + suffix;
        File targetFile = new File(GENERATED_IMAGE_DIR, fileName);
        HttpUtil.downloadFile(remoteUrl, targetFile);
        return "/api/public/images/" + fileName;
    }

    public String getGeneratedImageDir() {
        return GENERATED_IMAGE_DIR;
    }

    private String resolveSuffix(String url) {
        if (url == null || url.isBlank()) {
            return ".png";
        }
        String clean = url.split("\\?")[0];
        int dotIndex = clean.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == clean.length() - 1) {
            return ".png";
        }
        String suffix = clean.substring(dotIndex).toLowerCase();
        if (suffix.length() > 8) {
            return ".png";
        }
        return suffix;
    }
}
