package com.xcw.aiagentbackend;

import org.springframework.ai.autoconfigure.mcp.client.McpClientAutoConfiguration;
import org.springframework.ai.autoconfigure.vectorstore.pgvector.PgVectorStoreAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(exclude = {
        PgVectorStoreAutoConfiguration.class,
        McpClientAutoConfiguration.class
})
public class AiAgentBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiAgentBackendApplication.class, args);
    }

}
