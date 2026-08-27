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
import org.springframework.context.annotation.Lazy;

import java.util.ArrayList;
import java.util.List;

/**
 * MCP stdio 客户端懒加载：启动时不拉起 image-search / amap 子进程，
 * 首次真正调用 MCP 工具时才 spawn，缩短冷启动时间。
 */
@Configuration
public class LazyMcpClientConfiguration {

    @Bean
    @Lazy
    public ToolCallbackProvider mcpToolCallbackProvider(
            ObjectProvider<List<NamedClientMcpTransport>> transportsProvider,
            McpClientCommonProperties commonProperties) {

        return new ToolCallbackProvider() {
            private volatile ToolCallback[] callbacks;

            @Override
            public ToolCallback[] getToolCallbacks() {
                ToolCallback[] result = callbacks;
                if (result == null) {
                    synchronized (this) {
                        result = callbacks;
                        if (result == null) {
                            List<NamedClientMcpTransport> transports = transportsProvider.stream()
                                    .flatMap(List::stream)
                                    .toList();
                            result = buildToolCallbacks(transports, commonProperties);
                            callbacks = result;
                        }
                    }
                }
                return result;
            }
        };
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
