package com.xcw.aiagentbackend.service;

import com.xcw.aiagentbackend.config.ApiSecurityProperties;
import com.xcw.aiagentbackend.model.apikey.ApiKeyRecord;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ApiKeyService {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private ApiSecurityProperties apiSecurityProperties;

    @PostConstruct
    public void initTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS api_key_account (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  api_key VARCHAR(128) UNIQUE NOT NULL,
                  owner VARCHAR(128) NOT NULL,
                  status VARCHAR(32) NOT NULL,
                  quota INT NOT NULL DEFAULT 10000,
                  used_count INT NOT NULL DEFAULT 0,
                  expired_at DATETIME NULL,
                  created_at DATETIME NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS api_key_usage (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  api_key_id BIGINT NOT NULL,
                  request_id VARCHAR(128),
                  uri VARCHAR(256) NOT NULL,
                  method VARCHAR(16) NOT NULL,
                  status INT NOT NULL,
                  cost_ms BIGINT NOT NULL,
                  created_at DATETIME NOT NULL
                )
                """);

        List<String> seedKeys = apiSecurityProperties.getApiKeys();
        for (String key : seedKeys) {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM api_key_account WHERE api_key = ?", Integer.class, key);
            if (count != null && count == 0) {
                jdbcTemplate.update(
                        "INSERT INTO api_key_account(api_key, owner, status, quota, used_count, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                        key, "seed", "active", 100000, 0, LocalDateTime.now()
                );
            }
        }
    }

    public Optional<ApiKeyRecord> findActiveByKey(String apiKey) {
        return jdbcTemplate.query(
                "SELECT id, api_key, owner, status, quota, used_count, expired_at, created_at FROM api_key_account WHERE api_key = ? AND status = 'active'",
                this::mapRow,
                apiKey
        ).stream().filter(record -> record.getExpiredAt() == null || record.getExpiredAt().isAfter(LocalDateTime.now()))
                .findFirst()
                .filter(record -> record.getUsedCount() < record.getQuota());
    }

    public void recordUsage(Long apiKeyId, String requestId, String uri, String method, int status, long costMs) {
        jdbcTemplate.update(
                "INSERT INTO api_key_usage(api_key_id, request_id, uri, method, status, cost_ms, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                apiKeyId, requestId, uri, method, status, costMs, LocalDateTime.now()
        );
        jdbcTemplate.update("UPDATE api_key_account SET used_count = used_count + 1 WHERE id = ?", apiKeyId);
    }

    private ApiKeyRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return ApiKeyRecord.builder()
                .id(rs.getLong("id"))
                .apiKey(rs.getString("api_key"))
                .owner(rs.getString("owner"))
                .status(rs.getString("status"))
                .quota(rs.getInt("quota"))
                .usedCount(rs.getInt("used_count"))
                .expiredAt(rs.getTimestamp("expired_at") == null ? null : rs.getTimestamp("expired_at").toLocalDateTime())
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .build();
    }
}
