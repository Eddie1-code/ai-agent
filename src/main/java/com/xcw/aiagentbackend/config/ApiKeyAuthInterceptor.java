package com.xcw.aiagentbackend.config;

import jakarta.annotation.Resource;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
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

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 异步/错误重分发（如 SSE 流式完成后的再分发）不重复校验，避免与已占用的输出流冲突
        DispatcherType dispatcherType = request.getDispatcherType();
        if (dispatcherType == DispatcherType.ASYNC || dispatcherType == DispatcherType.ERROR) {
            return true;
        }
        request.setAttribute(START_AT, System.currentTimeMillis());
        String path = request.getRequestURI();
        if (path.contains("/swagger") || path.contains("/v3/api-docs") || path.contains("/health")) {
            return true;
        }
        // 已通过 JWT 登录的 Web 用户无需 API Key，直接放行，避免登录后被拦截器 401 踢下线
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream().anyMatch(a -> "ROLE_USER".equals(a.getAuthority()))) {
            return true;
        }
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = request.getParameter("apiKey");
        }
        if (apiKey == null || apiKey.isBlank() || !apiSecurityProperties.getApiKeys().contains(apiKey)) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "{\"code\":40100,\"message\":\"非法 API Key\",\"data\":null}");
            return false;
        }
        if (!allowRequest(apiKey, request.getRemoteAddr())) {
            writeError(response, 429, "{\"code\":42900,\"message\":\"请求过于频繁\",\"data\":null}");
            return false;
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) {
        Long startAt = (Long) request.getAttribute(START_AT);
        if (startAt != null) {
            long cost = System.currentTimeMillis() - startAt;
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

    private void writeError(HttpServletResponse response, int status, String body) {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        try {
            response.getWriter().write(body);
        } catch (IllegalStateException | IOException e) {
            // 输出流已被占用（SSE/文件流等场景），无法再写错误体，直接结束
            log.debug("无法写入错误响应: {}", e.getMessage());
        }
    }
}
