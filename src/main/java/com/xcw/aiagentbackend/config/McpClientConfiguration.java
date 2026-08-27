package com.xcw.aiagentbackend.config;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.autoconfigure.mcp.client.NamedClientMcpTransport;
import org.springframework.ai.autoconfigure.mcp.client.properties.McpClientCommonProperties;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * MCP stdio 客户端：启动时即拉起 amap / image-search 子进程并完成初始化，
 * 首次调用 MCP 工具时无需再等待冷启动。
 */
@Configuration
public class McpClientConfiguration {

    @Bean
    public ToolCallbackProvider mcpToolCallbackProvider(
            ObjectProvider<List<NamedClientMcpTransport>> transportsProvider,
            McpClientCommonProperties commonProperties) {

        List<NamedClientMcpTransport> transports = transportsProvider.stream()
                .flatMap(List::stream)
                .toList();
        ToolCallback[] callbacks = buildToolCallbacks(transports, commonProperties);
        return () -> callbacks;
    }

    private static ToolCallback[] buildToolCallbacks(
            List<NamedClientMcpTransport> transports,
            McpClientCommonProperties commonProperties) {

        McpSchema.Implementation clientInfo = new McpSchema.Implementation(
                commonProperties.getName(), commonProperties.getVersion());
        List<McpSyncClient> clients = new ArrayList<>();
        for (NamedClientMcpTransport named : transports) {
            McpSyncClient client = McpClient.sync(named.transport())
                    .clientInfo(clientInfo)
                    .requestTimeout(commonProperties.getRequestTimeout())
                    .build();
            client.initialize();
            clients.add(client);
        }
        return new SyncMcpToolCallbackProvider(clients).getToolCallbacks();
    }
}
