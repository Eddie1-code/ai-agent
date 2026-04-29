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
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.Map;

@Service
public class AsyncTaskService {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private MentorChatService mentorChatService;

    @Resource
    private ThreadPoolTaskExecutor appTaskExecutor;
    private final Map<String, Future<?>> taskFutures = new ConcurrentHashMap<>();

    @PostConstruct
    public void initTable() {
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS ai_async_task ("
                        + "task_id VARCHAR(64) PRIMARY KEY,"
                        + "request_id VARCHAR(64),"
                        + "mode VARCHAR(32) NOT NULL,"
                        + "status VARCHAR(32) NOT NULL,"
                        + "owner_type VARCHAR(32),"
                        + "owner_id VARCHAR(128),"
                        + "request_payload LONGTEXT,"
                        + "result_payload LONGTEXT,"
                        + "error_message LONGTEXT,"
                        + "cancel_reason LONGTEXT,"
                        + "created_at DATETIME NOT NULL,"
                        + "started_at DATETIME,"
                        + "finished_at DATETIME,"
                        + "updated_at DATETIME NOT NULL"
                        + ")"
        );
    }

    public String submit(TaskSubmitRequest request, String ownerType, String ownerId) {
        String taskId = UUID.randomUUID().toString();
        String requestId = request.getRequestId() == null || request.getRequestId().isBlank()
                ? UUID.randomUUID().toString() : request.getRequestId();
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "INSERT INTO ai_async_task(task_id, request_id, mode, status, owner_type, owner_id, request_payload, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                taskId,
                requestId,
                MentorMode.fromValue(request.getMode()).name(),
                "queued",
                ownerType,
                ownerId,
                request.getMessage(),
                now,
                now
        );
        Future<?> future = appTaskExecutor.submit(() -> executeTask(taskId, request));
        taskFutures.put(taskId, future);
        return taskId;
    }

    public Optional<AsyncTaskRecord> findByTaskId(String taskId) {
        return jdbcTemplate.query(
                "SELECT task_id, request_id, mode, status, owner_type, owner_id, request_payload, result_payload, error_message, cancel_reason, created_at, started_at, finished_at, updated_at "
                        + "FROM ai_async_task WHERE task_id = ?",
                new TaskRowMapper(), taskId
        ).stream().findFirst();
    }

    public List<AsyncTaskRecord> listLatest(int limit) {
        return jdbcTemplate.query(
                "SELECT task_id, request_id, mode, status, owner_type, owner_id, request_payload, result_payload, error_message, cancel_reason, created_at, started_at, finished_at, updated_at "
                        + "FROM ai_async_task ORDER BY created_at DESC LIMIT ?",
                new TaskRowMapper(), limit
        );
    }

    public boolean cancelTask(String taskId, String reason) {
        Future<?> future = taskFutures.remove(taskId);
        if (future != null) {
            future.cancel(true);
        }
        int updated = jdbcTemplate.update(
                "UPDATE ai_async_task SET status = ?, cancel_reason = ?, finished_at = ?, updated_at = ? "
                        + "WHERE task_id = ? AND status IN ('queued', 'running')",
                "cancelled",
                reason,
                LocalDateTime.now(),
                LocalDateTime.now(),
                taskId
        );
        return updated > 0;
    }

    private void executeTask(String taskId, TaskSubmitRequest request) {
        updateStatus(taskId, "running", null, null, null, true, false);
        try {
            String output = mentorChatService.chatByStream(
                            MentorMode.fromValue(request.getMode()),
                            request.getMessage(),
                            request.getChatId()
                    )
                    .collectList()
                    .map(list -> String.join("\n", list))
                    .block();
            updateStatus(taskId, "succeeded", output, null, null, false, true);
        } catch (Exception e) {
            if (Thread.currentThread().isInterrupted()) {
                updateStatus(taskId, "cancelled", null, null, "任务被中断", false, true);
                return;
            }
            updateStatus(taskId, "failed", null, e.getMessage(), null, false, true);
        } finally {
            taskFutures.remove(taskId);
        }
    }

    private void updateStatus(String taskId, String status, String resultPayload, String errorMessage, String cancelReason,
                              boolean setStartedAt, boolean setFinishedAt) {
        LocalDateTime now = LocalDateTime.now();
        if (setStartedAt) {
            jdbcTemplate.update(
                    "UPDATE ai_async_task SET status = ?, result_payload = ?, error_message = ?, cancel_reason = ?, started_at = COALESCE(started_at, ?), updated_at = ? "
                            + "WHERE task_id = ?",
                    status,
                    resultPayload,
                    errorMessage,
                    cancelReason,
                    now,
                    now,
                    taskId
            );
            return;
        }
        if (setFinishedAt) {
            jdbcTemplate.update(
                    "UPDATE ai_async_task SET status = ?, result_payload = ?, error_message = ?, cancel_reason = ?, finished_at = ?, updated_at = ? "
                            + "WHERE task_id = ?",
                    status,
                    resultPayload,
                    errorMessage,
                    cancelReason,
                    now,
                    now,
                    taskId
            );
            return;
        }
        jdbcTemplate.update(
                "UPDATE ai_async_task SET status = ?, result_payload = ?, error_message = ?, cancel_reason = ?, updated_at = ? "
                        + "WHERE task_id = ?",
                status,
                resultPayload,
                errorMessage,
                cancelReason,
                now,
                taskId
        );
    }

    private static LocalDateTime getNullableDateTime(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column) == null ? null : rs.getTimestamp(column).toLocalDateTime();
    }

    private static class TaskRowMapper implements RowMapper<AsyncTaskRecord> {
        @Override
        public AsyncTaskRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return AsyncTaskRecord.builder()
                    .taskId(rs.getString("task_id"))
                    .requestId(rs.getString("request_id"))
                    .mode(rs.getString("mode"))
                    .status(rs.getString("status"))
                    .ownerType(rs.getString("owner_type"))
                    .ownerId(rs.getString("owner_id"))
                    .requestPayload(rs.getString("request_payload"))
                    .resultPayload(rs.getString("result_payload"))
                    .errorMessage(rs.getString("error_message"))
                    .cancelReason(rs.getString("cancel_reason"))
                    .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                    .startedAt(getNullableDateTime(rs, "started_at"))
                    .finishedAt(getNullableDateTime(rs, "finished_at"))
                    .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
                    .build();
        }
    }
}
