package com.xcw.aiagentbackend.service;

import com.xcw.aiagentbackend.exception.BusinessException;
import com.xcw.aiagentbackend.exception.ErrorCode;
import com.xcw.aiagentbackend.model.auth.UserAccount;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    @Resource
    private JdbcTemplate jdbcTemplate;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @PostConstruct
    public void initTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS user_account (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  username VARCHAR(128) UNIQUE NOT NULL,
                  nickname VARCHAR(128),
                  avatar_url VARCHAR(255),
                  password_hash VARCHAR(255) NOT NULL,
                  created_at DATETIME NOT NULL,
                  updated_at DATETIME NOT NULL
                )
                """);
    }

    public UserAccount register(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户名或密码不能为空");
        }
        if (findByUsername(username).isPresent()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "用户名已存在");
        }
        String hash = passwordEncoder.encode(password);
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("INSERT INTO user_account(username, nickname, avatar_url, password_hash, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                username, username, null, hash, now, now);
        return findByUsername(username).orElseThrow(() -> new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败"));
    }

    public UserAccount login(String username, String password) {
        UserAccount user = findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "用户不存在"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "用户名或密码错误");
        }
        return user;
    }

    public Optional<UserAccount> findByUsername(String username) {
        return jdbcTemplate.query("SELECT id, username, nickname, avatar_url, password_hash, created_at, updated_at FROM user_account WHERE username = ?",
                this::mapRow, username).stream().findFirst();
    }

    public UserAccount getCurrentUser(String username) {
        Optional<UserAccount> existing = findByUsername(username);
        if (existing.isPresent()) {
            return existing.get();
        }
        if (username != null && username.startsWith("apikey:")) {
            return createShadowUserForApiKey(username);
        }
        throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "用户不存在");
    }

    private UserAccount createShadowUserForApiKey(String username) {
        LocalDateTime now = LocalDateTime.now();
        String hash = passwordEncoder.encode("shadow-" + UUID.randomUUID());
        jdbcTemplate.update(
                "INSERT INTO user_account(username, nickname, avatar_url, password_hash, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                username, username, null, hash, now, now
        );
        return findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.SYSTEM_ERROR, "影子用户创建失败"));
    }

    public UserAccount updateProfile(String username, String nickname, String avatarUrl) {
        UserAccount currentUser = getCurrentUser(username);
        String nextNickname = (nickname == null || nickname.isBlank()) ? currentUser.getNickname() : nickname.trim();
        String nextAvatarUrl = avatarUrl == null ? currentUser.getAvatarUrl() : avatarUrl.trim();
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("UPDATE user_account SET nickname = ?, avatar_url = ?, updated_at = ? WHERE username = ?",
                nextNickname, nextAvatarUrl, now, username);
        return getCurrentUser(username);
    }

    public void changePassword(String username, String oldPassword, String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "新密码不能为空");
        }
        UserAccount currentUser = getCurrentUser(username);
        if (!passwordEncoder.matches(oldPassword, currentUser.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "旧密码错误");
        }
        String hash = passwordEncoder.encode(newPassword);
        jdbcTemplate.update("UPDATE user_account SET password_hash = ?, updated_at = ? WHERE username = ?",
                hash, LocalDateTime.now(), username);
    }

    private UserAccount mapRow(ResultSet rs, int rowNum) throws SQLException {
        return UserAccount.builder()
                .id(rs.getLong("id"))
                .username(rs.getString("username"))
                .nickname(rs.getString("nickname"))
                .avatarUrl(rs.getString("avatar_url"))
                .passwordHash(rs.getString("password_hash"))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
                .build();
    }
}
