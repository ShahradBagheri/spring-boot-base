package com.notshahrad.spring_boot_base.mcp;

import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.springframework.ai.mcp.annotation.McpPrompt;

import java.util.List;

public final class FeatureIntegrationPrompt {

    @McpPrompt(name = "plan_feature_integration", description = "Draft an integration checklist for a base feature.")
    public GetPromptResult planIntegration() {
        var message = new TextContent("""
                Draft an integration checklist for the Spring Boot Base feature the user selected.
                Use the feature's setup guide as your source. Cover dependencies, configuration, and verification.
                Use placeholders for secrets. Mark settings that depend on the consuming application.
                """);
        return new GetPromptResult("Feature integration checklist", List.of(new PromptMessage(Role.USER, message)));
    }
}
