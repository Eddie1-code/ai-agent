package com.xcw.aiagentbackend.config;

import com.xcw.aiagentbackend.model.apikey.ApiKeyRecord;
import com.xcw.aiagentbackend.service.ApiKeyService;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    @Resource
    private ApiKeyService apiKeyService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }
        String apiKey = request.getHeader("X-API-Key");
        if ((apiKey == null || apiKey.isBlank()) && request.getParameter("apiKey") != null) {
            apiKey = request.getParameter("apiKey");
        }
        if (apiKey != null && !apiKey.isBlank()) {
            Optional<ApiKeyRecord> recordOpt = apiKeyService.findActiveByKey(apiKey);
            if (recordOpt.isPresent()) {
                ApiKeyRecord record = recordOpt.get();
                request.setAttribute("apiKeyId", record.getId());
                request.setAttribute("apiKey", record.getApiKey());
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        "apikey:" + record.getOwner(), null, List.of(new SimpleGrantedAuthority("ROLE_API")));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }
}
