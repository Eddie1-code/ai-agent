package com.xcw.aiagentbackend.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import com.xcw.aiagentbackend.service.ApiKeyService;
import jakarta.annotation.Resource;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class ApiKeyAuthInterceptor implements HandlerInterceptor {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String START_AT = "requestStartAt";

    private final Map<String, ArrayDeque<Long>> requestTimestamps = new ConcurrentHashMap<>();

    @Resource
    private ApiSecurityProperties apiSecurityProperties;

    @Resource
    private ApiKeyService apiKeyService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        request.setAttribute(START_AT, System.currentTimeMillis());
        String apiKey = (String) request.getAttribute("apiKey");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = request.getHeader(API_KEY_HEADER);
        }
        if (apiKey == null || apiKey.isBlank()) {
            return true;
        }
        if (!allowRequest(apiKey, request.getRemoteAddr())) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":42900,\"message\":\"请求过于频繁\",\"data\":null}");
            return false;
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) {
        Long startAt = (Long) request.getAttribute(START_AT);
        if (startAt != null) {
            long cost = System.currentTimeMillis() - startAt;
            Object apiKeyId = request.getAttribute("apiKeyId");
            if (apiKeyId instanceof Long keyId) {
                String requestId = request.getParameter("requestId");
                apiKeyService.recordUsage(keyId, requestId, request.getRequestURI(), request.getMethod(), response.getStatus(), cost);
            }
            log.info("api_usage uri={} method={} status={} costMs={} apiKey={}",
                    request.getRequestURI(),
                    request.getMethod(),
                    response.getStatus(),
                    cost,
                    request.getHeader(API_KEY_HEADER));
        }
    }

    private boolean allowRequest(String apiKey, String ip) {
        long now = System.currentTimeMillis();
        long minTs = now - 60_000L;
        String bucketKey = apiKey + ":" + ip;
        ArrayDeque<Long> deque = requestTimestamps.computeIfAbsent(bucketKey, key -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst() < minTs) {
                deque.pollFirst();
            }
            if (deque.size() >= apiSecurityProperties.getMaxRequestsPerMinute()) {
                return false;
            }
            deque.addLast(now);
            return true;
        }
    }
}
