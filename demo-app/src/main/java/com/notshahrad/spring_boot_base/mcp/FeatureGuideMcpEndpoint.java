package com.notshahrad.spring_boot_base.mcp;

import com.notshahrad.spring_boot_base.mcp.FeatureGuideCatalog.FeatureGuide;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;

import java.util.List;

public final class FeatureGuideMcpEndpoint {

    static final String INDEX_URI = "spring-boot-base://guides/index";

    private final FeatureGuideCatalog catalog;

    FeatureGuideMcpEndpoint(FeatureGuideCatalog catalog) {
        this.catalog = catalog;
    }

    @McpTool(name = "lookup_feature_guide",
            description = "Find the setup guide for one Spring Boot Base feature.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false))
    public FeatureGuide lookup(
            @McpToolParam(description = "Feature key. Use oidc, email, phone, guest, or token.", required = true)
            String feature) {
        return catalog.lookup(feature);
    }

    @McpResource(uri = INDEX_URI, name = "feature-guide-index",
            description = "Lists the feature keys and their setup guides.", mimeType = "text/plain")
    public ReadResourceResult guideIndex() {
        return new ReadResourceResult(List.of(new TextResourceContents(INDEX_URI, "text/plain", catalog.index())));
    }
}
