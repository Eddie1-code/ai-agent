package com.xcw.aiagentbackend.service;

import com.xcw.aiagentbackend.config.ApiSecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    @Resource
    private ApiSecurityProperties apiSecurityProperties;

    public String generateToken(String username) {
        Instant now = Instant.now();
        Instant expireAt = now.plusSeconds(apiSecurityProperties.getJwtExpireSeconds());
        return Jwts.builder()
                .subject(username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expireAt))
                .signWith(secretKey())
                .compact();
    }

    public String parseUsername(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(apiSecurityProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8));
    }
}
