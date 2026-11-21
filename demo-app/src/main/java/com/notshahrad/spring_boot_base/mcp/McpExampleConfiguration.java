package com.notshahrad.spring_boot_base.mcp;

import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncPromptSpecification;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import org.springframework.ai.mcp.annotation.spring.SyncMcpAnnotationProviders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = {"base.mcp.server.enabled", "demo.mcp.examples.enabled"}, havingValue = "true")
@ConditionalOnProperty(prefix = "spring.ai.mcp.server", name = "type", havingValue = "SYNC", matchIfMissing = true)
class McpExampleConfiguration {

    @Bean
    FeatureGuideMcpEndpoint featureGuideMcpEndpoint() {
        return new FeatureGuideMcpEndpoint(new FeatureGuideCatalog());
    }

    @Bean
    List<SyncToolSpecification> featureGuideTools(FeatureGuideMcpEndpoint endpoint) {
        return SyncMcpAnnotationProviders.statelessToolSpecifications(List.of(endpoint));
    }

    @Bean
    List<SyncResourceSpecification> featureGuideResources(FeatureGuideMcpEndpoint endpoint) {
        return SyncMcpAnnotationProviders.statelessResourceSpecifications(List.of(endpoint));
    }

    @Bean
    List<SyncPromptSpecification> featureIntegrationPrompts() {
        return SyncMcpAnnotationProviders.statelessPromptSpecifications(List.of(new FeatureIntegrationPrompt()));
    }
}
