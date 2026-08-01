package com.jaspermsnbk.ai.basic_mcp.config;

import com.jaspermsnbk.ai.basic_mcp.mcp.DocumentMcpTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodInvokingToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpConfig {

    @Bean
    public ToolCallbackProvider documentTools(DocumentMcpTools tools) {
        return MethodInvokingToolCallbackProvider.builder().toolObjects(tools).build();
    }
}
