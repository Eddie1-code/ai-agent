package com.xcw.aiagentbackend.service;

import com.xcw.aiagentbackend.exception.BusinessException;
import com.xcw.aiagentbackend.exception.ErrorCode;
import com.xcw.aiagentbackend.model.auth.UserAccount;
import com.xcw.aiagentbackend.model.chat.ChatMessageRecord;
import com.xcw.aiagentbackend.model.chat.ChatSessionRecord;
import com.xcw.aiagentbackend.model.chat.ExportRecord;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ChatSessionService {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private AuthService authService;

    @PostConstruct
    public void initTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS chat_session (
                  id VARCHAR(64) PRIMARY KEY,
                  user_id BIGINT NOT NULL,
                  title VARCHAR(128) NOT NULL,
                  mode VARCHAR(16) NOT NULL,
                  archived TINYINT(1) NOT NULL DEFAULT 0,
                  created_at DATETIME NOT NULL,
                  updated_at DATETIME NOT NULL,
                  INDEX idx_chat_session_user_updated (user_id, updated_at)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS chat_message (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  session_id VARCHAR(64) NOT NULL,
                  user_id BIGINT NOT NULL,
                  role VARCHAR(16) NOT NULL,
                  event_type VARCHAR(32) NOT NULL,
                  content MEDIUMTEXT NOT NULL,
                  metadata_json TEXT,
                  created_at DATETIME NOT NULL,
                  INDEX idx_chat_message_session_created (session_id, created_at)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS export_record (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  user_id BIGINT NOT NULL,
                  session_id VARCHAR(64) NOT NULL,
                  source_message_id BIGINT,
                  file_path VARCHAR(255) NOT NULL,
                  created_at DATETIME NOT NULL
                )
                """);
        ensureSchemaCompatible();
    }

    private void ensureSchemaCompatible() {
        if (!hasColumn("chat_message", "role")) {
            jdbcTemplate.execute("ALTER TABLE chat_message ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'assistant' AFTER user_id");
        }
        if (!hasColumn("chat_message", "user_id")) {
            jdbcTemplate.execute("ALTER TABLE chat_message ADD COLUMN user_id BIGINT NULL AFTER session_id");
            jdbcTemplate.execute("""
                    UPDATE chat_message m
                    JOIN chat_session s ON m.session_id = s.id
                    SET m.user_id = s.user_id
                    WHERE m.user_id IS NULL
                    """);
        }
        if (!hasColumn("chat_message", "event_type")) {
            jdbcTemplate.execute("ALTER TABLE chat_message ADD COLUMN event_type VARCHAR(32) NOT NULL DEFAULT 'answer' AFTER role");
        }
        if (!hasColumn("chat_message", "metadata_json")) {
            jdbcTemplate.execute("ALTER TABLE chat_message ADD COLUMN metadata_json TEXT NULL AFTER content");
        }
        if (!hasColumn("chat_message", "created_at")) {
            jdbcTemplate.execute("ALTER TABLE chat_message ADD COLUMN created_at DATETIME NULL AFTER metadata_json");
            jdbcTemplate.execute("UPDATE chat_message SET created_at = NOW() WHERE created_at IS NULL");
        }
    }

    private boolean hasColumn(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(1)
                        FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_NAME = ?
                          AND COLUMN_NAME = ?
                        """,
                Integer.class,
                tableName, columnName);
        return count != null && count > 0;
    }

    public List<ChatSessionRecord> listSessions(String username) {
        UserAccount user = authService.getCurrentUser(username);
        return jdbcTemplate.query("""
                        SELECT id, user_id, title, mode, archived, created_at, updated_at
                        FROM chat_session
                        WHERE user_id = ?
                        ORDER BY updated_at DESC
                        """,
                this::mapSession,
                user.getId());
    }

    public ChatSessionRecord createSession(String username, String title, String mode) {
        UserAccount user = authService.getCurrentUser(username);
        String sessionId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        String safeTitle = (title == null || title.isBlank()) ? "新会话" : title.trim();
        String safeMode = (mode == null || mode.isBlank()) ? "coach" : mode.trim();
        jdbcTemplate.update("""
                        INSERT INTO chat_session(id, user_id, title, mode, archived, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                sessionId, user.getId(), safeTitle, safeMode, 0, now, now);
        return getOwnedSessionOrThrow(user.getId(), sessionId);
    }

    public ChatSessionRecord ensureSession(String username, String sessionId, String mode, String firstMessage) {
        UserAccount user = authService.getCurrentUser(username);
        String safeMode = (mode == null || mode.isBlank()) ? "coach" : mode.trim();
        String safeTitle = buildTitle(firstMessage);
        Optional<ChatSessionRecord> existing = findByIdAndUserId(sessionId, user.getId());
        if (existing.isPresent()) {
            touchSession(sessionId);
            return existing.get();
        }
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                        INSERT INTO chat_session(id, user_id, title, mode, archived, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                sessionId, user.getId(), safeTitle, safeMode, 0, now, now);
        return getOwnedSessionOrThrow(user.getId(), sessionId);
    }

    public ChatSessionRecord updateSession(String username, String sessionId, String title, Boolean archived) {
        UserAccount user = authService.getCurrentUser(username);
        ChatSessionRecord old = getOwnedSessionOrThrow(user.getId(), sessionId);
        String nextTitle = (title == null || title.isBlank()) ? old.getTitle() : title.trim();
        boolean nextArchived = archived == null ? old.getArchived() : archived;
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                        UPDATE chat_session SET title = ?, archived = ?, updated_at = ?
                        WHERE id = ? AND user_id = ?
                        """,
                nextTitle, nextArchived ? 1 : 0, now, sessionId, user.getId());
        return getOwnedSessionOrThrow(user.getId(), sessionId);
    }

    public void deleteSession(String username, String sessionId) {
        UserAccount user = authService.getCurrentUser(username);
        getOwnedSessionOrThrow(user.getId(), sessionId);
        jdbcTemplate.update("DELETE FROM chat_message WHERE session_id = ? AND user_id = ?", sessionId, user.getId());
        jdbcTemplate.update("DELETE FROM chat_session WHERE id = ? AND user_id = ?", sessionId, user.getId());
    }

    public List<ChatMessageRecord> listMessages(String username, String sessionId, int limit) {
        UserAccount user = authService.getCurrentUser(username);
        getOwnedSessionOrThrow(user.getId(), sessionId);
        int finalLimit = Math.max(1, Math.min(limit, 200));
        return jdbcTemplate.query("""
                        SELECT id, session_id, user_id, role, event_type, content, metadata_json, created_at
                        FROM chat_message
                        WHERE session_id = ? AND user_id = ?
                        ORDER BY id DESC
                        LIMIT ?
                        """,
                (rs, rowNum) -> mapMessage(rs),
                sessionId, user.getId(), finalLimit)
                .reversed();
    }

    public void appendMessage(String username, String sessionId, String role, String eventType, String content, String metadataJson) {
        UserAccount user = authService.getCurrentUser(username);
        getOwnedSessionOrThrow(user.getId(), sessionId);
        jdbcTemplate.update("""
                        INSERT INTO chat_message(session_id, user_id, role, event_type, content, metadata_json, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                sessionId, user.getId(), role, eventType, content, metadataJson, LocalDateTime.now());
        if ("user".equalsIgnoreCase(role) && content != null && !content.isBlank()) {
            String nextTitle = buildTitle(content);
            jdbcTemplate.update("""
                            UPDATE chat_session
                            SET title = ?, updated_at = ?
                            WHERE id = ? AND user_id = ?
                              AND (title IS NULL OR title = '' OR title = '新会话')
                            """,
                    nextTitle, LocalDateTime.now(), sessionId, user.getId());
        }
        touchSession(sessionId);
    }

    public ChatMessageRecord latestAssistantMessage(String username, String sessionId) {
        UserAccount user = authService.getCurrentUser(username);
        getOwnedSessionOrThrow(user.getId(), sessionId);
        return jdbcTemplate.query("""
                        SELECT id, session_id, user_id, role, event_type, content, metadata_json, created_at
                        FROM chat_message
                        WHERE session_id = ? AND user_id = ? AND role = 'assistant'
                        ORDER BY id DESC
                        LIMIT 1
                        """,
                (rs, rowNum) -> mapMessage(rs),
                sessionId, user.getId()).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "暂无可导出的计划"));
    }

    public ExportRecord saveExportRecord(String username, String sessionId, Long sourceMessageId, String filePath) {
        UserAccount user = authService.getCurrentUser(username);
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                        INSERT INTO export_record(user_id, session_id, source_message_id, file_path, created_at)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                user.getId(), sessionId, sourceMessageId, filePath, now);
        return jdbcTemplate.query("""
                        SELECT id, user_id, session_id, source_message_id, file_path, created_at
                        FROM export_record
                        WHERE user_id = ? AND session_id = ?
                        ORDER BY id DESC
                        LIMIT 1
                        """,
                (rs, rowNum) -> ExportRecord.builder()
                        .id(rs.getLong("id"))
                        .userId(rs.getLong("user_id"))
                        .sessionId(rs.getString("session_id"))
                        .sourceMessageId(rs.getLong("source_message_id"))
                        .filePath(rs.getString("file_path"))
                        .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                        .build(),
                user.getId(), sessionId).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.SYSTEM_ERROR, "导出记录写入失败"));
    }

    public ExportRecord getExportRecord(String username, Long exportId) {
        UserAccount user = authService.getCurrentUser(username);
        return jdbcTemplate.query("""
                        SELECT id, user_id, session_id, source_message_id, file_path, created_at
                        FROM export_record
                        WHERE id = ? AND user_id = ?
                        """,
                (rs, rowNum) -> ExportRecord.builder()
                        .id(rs.getLong("id"))
                        .userId(rs.getLong("user_id"))
                        .sessionId(rs.getString("session_id"))
                        .sourceMessageId(rs.getLong("source_message_id"))
                        .filePath(rs.getString("file_path"))
                        .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                        .build(),
                exportId, user.getId()).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "导出文件不存在"));
    }

    public void touchSession(String sessionId) {
        jdbcTemplate.update("UPDATE chat_session SET updated_at = ? WHERE id = ?", LocalDateTime.now(), sessionId);
    }

    private Optional<ChatSessionRecord> findByIdAndUserId(String sessionId, Long userId) {
        return jdbcTemplate.query("""
                        SELECT id, user_id, title, mode, archived, created_at, updated_at
                        FROM chat_session
                        WHERE id = ? AND user_id = ?
                        """,
                this::mapSession,
                sessionId, userId).stream().findFirst();
    }

    private ChatSessionRecord getOwnedSessionOrThrow(Long userId, String sessionId) {
        return findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "会话不存在"));
    }

    private String buildTitle(String firstMessage) {
        if (firstMessage == null || firstMessage.isBlank()) {
            return "新会话";
        }
        String content = firstMessage.trim();
        return content.length() <= 16 ? content : content.substring(0, 16);
    }

    private ChatSessionRecord mapSession(ResultSet rs, int rowNum) throws SQLException {
        return ChatSessionRecord.builder()
                .id(rs.getString("id"))
                .userId(rs.getLong("user_id"))
                .title(rs.getString("title"))
                .mode(rs.getString("mode"))
                .archived(rs.getInt("archived") == 1)
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
                .build();
    }

    private ChatMessageRecord mapMessage(ResultSet rs) throws SQLException {
        return ChatMessageRecord.builder()
                .id(rs.getLong("id"))
                .sessionId(rs.getString("session_id"))
                .userId(rs.getLong("user_id"))
                .role(rs.getString("role"))
                .eventType(rs.getString("event_type"))
                .content(rs.getString("content"))
                .metadataJson(rs.getString("metadata_json"))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .build();
    }
}
