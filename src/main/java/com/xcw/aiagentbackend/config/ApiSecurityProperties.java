package com.xcw.aiagentbackend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "app.security")
public class ApiSecurityProperties {
    private List<String> apiKeys = new ArrayList<>();
    private int maxRequestsPerMinute = 60;
}
