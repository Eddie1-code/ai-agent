package com.xcw.aiagentbackend.config;

import org.springframework.context.annotation.Configuration;

/**
 * MCP Client 可用时由 McpClientAutoConfiguration 提供 ToolCallbackProvider；
 * 不可用时 MentorApp 中 @Autowired(required = false) 自动为 null，走降级文案。
 * 这里不再注册本地 ToolCallbackProvider，避免与 MCP 自动配置的 Bean 冲突。
 */
@Configuration
public class McpFallbackConfiguration {
}
