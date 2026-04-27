package com.xcw.aiagentbackend.service;

import com.xcw.aiagentbackend.model.chat.MentorMode;
import com.xcw.aiagentbackend.model.task.AsyncTaskRecord;
import com.xcw.aiagentbackend.model.task.TaskSubmitRequest;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class AsyncTaskService {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private MentorChatService mentorChatService;

    @Resource
    private ThreadPoolTaskExecutor appTaskExecutor;

    @PostConstruct
    public void initTable() {
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS ai_async_task ("
                        + "task_id VARCHAR(64) PRIMARY KEY,"
                        + "mode VARCHAR(32) NOT NULL,"
                        + "status VARCHAR(32) NOT NULL,"
                        + "request_payload CLOB,"
                        + "result_payload CLOB,"
                        + "error_message CLOB,"
                        + "created_at TIMESTAMP NOT NULL,"
                        + "updated_at TIMESTAMP NOT NULL"
                        + ")"
        );
    }

    public String submit(TaskSubmitRequest request) {
        String taskId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "INSERT INTO ai_async_task(task_id, mode, status, request_payload, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                taskId,
                MentorMode.fromValue(request.getMode()).name(),
                "queued",
                request.getMessage(),
                now,
                now
        );
        appTaskExecutor.submit(() -> executeTask(taskId, request));
        return taskId;
    }

    public Optional<AsyncTaskRecord> findByTaskId(String taskId) {
        return jdbcTemplate.query(
                "SELECT task_id, mode, status, request_payload, result_payload, error_message, created_at, updated_at "
                        + "FROM ai_async_task WHERE task_id = ?",
                new TaskRowMapper(), taskId
        ).stream().findFirst();
    }

    private void executeTask(String taskId, TaskSubmitRequest request) {
        updateStatus(taskId, "running", null, null);
        try {
            String output = mentorChatService.chatByStream(
                            MentorMode.fromValue(request.getMode()),
                            request.getMessage(),
                            request.getChatId()
                    )
                    .collectList()
                    .map(list -> String.join("\n", list))
                    .block();
            updateStatus(taskId, "succeeded", output, null);
        } catch (Exception e) {
            updateStatus(taskId, "failed", null, e.getMessage());
        }
    }

    private void updateStatus(String taskId, String status, String resultPayload, String errorMessage) {
        jdbcTemplate.update(
                "UPDATE ai_async_task SET status = ?, result_payload = ?, error_message = ?, updated_at = ? "
                        + "WHERE task_id = ?",
                status,
                resultPayload,
                errorMessage,
                LocalDateTime.now(),
                taskId
        );
    }

    private static class TaskRowMapper implements RowMapper<AsyncTaskRecord> {
        @Override
        public AsyncTaskRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return AsyncTaskRecord.builder()
                    .taskId(rs.getString("task_id"))
                    .mode(rs.getString("mode"))
                    .status(rs.getString("status"))
                    .requestPayload(rs.getString("request_payload"))
                    .resultPayload(rs.getString("result_payload"))
                    .errorMessage(rs.getString("error_message"))
                    .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                    .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
                    .build();
        }
    }
}
