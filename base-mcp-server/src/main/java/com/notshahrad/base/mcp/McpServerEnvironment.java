package com.notshahrad.base.mcp;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

public final class McpServerEnvironment implements EnvironmentPostProcessor, Ordered {

    static final String ENABLED = "base.mcp.server.enabled";
    static final String ENDPOINT = "/mcp";

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        boolean enabled = environment.getProperty(ENABLED, Boolean.class, false);
        // The transport and its dedicated security chain must always agree on the route.
        environment.getPropertySources().addFirst(new MapPropertySource("baseMcpTransport", Map.of(
                "spring.ai.mcp.server.enabled", enabled,
                "spring.ai.mcp.server.protocol", "STATELESS",
                "spring.ai.mcp.server.stdio", false,
                "spring.ai.mcp.server.tool-callback-converter", false,
                "spring.ai.mcp.server.expose-mcp-client-tools", false,
                "spring.ai.mcp.server.streamable-http.mcp-endpoint", ENDPOINT)));
    }
}
