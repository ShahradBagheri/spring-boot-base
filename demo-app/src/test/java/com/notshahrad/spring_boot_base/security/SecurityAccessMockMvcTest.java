package com.notshahrad.spring_boot_base.security;

import com.notshahrad.spring_boot_base.auth.email.EmailIdentityRepository;
import com.notshahrad.spring_boot_base.auth.phone.PhoneIdentityRepository;
import com.notshahrad.spring_boot_base.auth.phone.PhoneOtpService;
import com.notshahrad.spring_boot_base.auth.token.AccessTokenIssuer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.ai.mcp.server.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityAccessMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Test
    void defaultConfigurationHasNoTokenIssuerOrTokenEndpoint() {
        assertTrue(context.getBeansOfType(AccessTokenIssuer.class).isEmpty());
        assertTrue(requestMappingHandlerMapping.getHandlerMethods().keySet().stream()
                .noneMatch(mapping -> mapping.getPatternValues().stream().anyMatch(path -> path.startsWith("/api/token/"))));
    }

    @Test
    void defaultConfigurationDoesNotEnableEmailIdentityStorage() {
        assertTrue(context.getBeansOfType(EmailIdentityRepository.class).isEmpty());
    }

    @Test
    void defaultConfigurationDoesNotEnablePhoneIdentityStorage() {
        assertTrue(context.getBeansOfType(PhoneIdentityRepository.class).isEmpty());
        assertTrue(context.getBeansOfType(PhoneOtpService.class).isEmpty());
    }

    @Test
    @WithAnonymousUser
    void defaultConfigurationDoesNotExposeGuestRoutes() throws Exception {
        assertTrue(requestMappingHandlerMapping.getHandlerMethods().keySet().stream()
                .noneMatch(mapping -> mapping.getPatternValues().stream().anyMatch(path -> path.startsWith("/api/guest/"))));
        mockMvc.perform(get("/api/guest/csrf")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithAnonymousUser
    void defaultConfigurationDoesNotExposeMcpEvenIfStarterIsEnabled() throws Exception {
        assertFalse(context.containsBean("webMvcStatelessServerRouterFunction"));
        assertFalse(context.containsBean("mcpSecurityFilterChain"));
        mockMvc.perform(post("/mcp").with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/.well-known/oauth-protected-resource/mcp")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithAnonymousUser
    void healthEndpointRemainsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(head("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    @WithAnonymousUser
    void unauthenticatedApplicationRequestReturnsApiError401() throws Exception {
        mockMvc.perform(get("/api/example/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication required"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @Test
    @WithAnonymousUser
    void unknownRouteIsNotPublicByDefault() throws Exception {
        mockMvc.perform(get("/api/not-registered"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @WithAnonymousUser
    void otherActuatorEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @WithMockUser(username = "integration-user")
    void authenticatedUserCanAccessProtectedExample() throws Exception {
        mockMvc.perform(get("/api/example/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("integration-user"));
    }

    @Test
    @WithMockUser(username = "integration-user", roles = "USER")
    void authenticatedUserWithoutAdminRoleReceivesApiError403() throws Exception {
        mockMvc.perform(get("/api/example/admin"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access denied"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @Test
    @WithMockUser(username = "integration-admin", roles = "ADMIN")
    void authorizedAdminCanAccessAdminExample() throws Exception {
        mockMvc.perform(get("/api/example/admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Admin access granted"));
    }
}
