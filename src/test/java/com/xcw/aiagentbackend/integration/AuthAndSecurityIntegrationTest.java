package com.xcw.aiagentbackend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xcw.aiagentbackend.model.chat.ChatStreamRequest;
import com.xcw.aiagentbackend.model.task.AsyncTaskRecord;
import com.xcw.aiagentbackend.service.AsyncTaskService;
import com.xcw.aiagentbackend.service.StreamSessionManager;
import com.xcw.aiagentbackend.demo.invoke.SpringAiAiInvoke;
import com.xcw.aiagentbackend.demo.invoke.OllamaAiInvoke;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb_auth;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "server.servlet.context-path=",
        "spring.ai.dashscope.api-key=test-dashscope-key",
        "app.security.jwt-secret=test-jwt-secret-test-jwt-secret-12345"
})
class AuthAndSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AsyncTaskService asyncTaskService;

    @MockBean
    private StreamSessionManager streamSessionManager;

    @MockBean(name = "mentorVectorStore")
    private VectorStore mentorVectorStore;

    @MockBean
    private SpringAiAiInvoke springAiAiInvoke;

    @MockBean
    private OllamaAiInvoke ollamaAiInvoke;

    @Test
    void unauthenticatedTaskEndpointShouldReturn401() throws Exception {
        mockMvc.perform(get("/task/latest"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void registerLoginAndCallMeShouldWork() throws Exception {
        String registerBody = """
                {"username":"it_user","password":"it_pass_123"}
                """;
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        String loginResp = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode jsonNode = objectMapper.readTree(loginResp);
        String token = jsonNode.path("data").path("token").asText();

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value("it_user"));
    }

    @Test
    void apiKeyShouldAllowTaskLatestAndStopEndpoint() throws Exception {
        AsyncTaskRecord record = AsyncTaskRecord.builder()
                .taskId("task-1")
                .requestId("req-1")
                .mode("COACH")
                .status("queued")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        Mockito.when(asyncTaskService.listLatest(anyInt())).thenReturn(List.of(record));
        Mockito.when(streamSessionManager.hasSession(anyString())).thenReturn(false);

        mockMvc.perform(get("/task/latest")
                        .header("X-API-Key", "demo-local-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].taskId").value("task-1"));

        ChatStreamRequest stopRequest = new ChatStreamRequest();
        stopRequest.setRequestId("req-not-found");
        mockMvc.perform(post("/ai/mentor/chat/stop")
                        .header("X-API-Key", "demo-local-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(stopRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(false));
    }
}
