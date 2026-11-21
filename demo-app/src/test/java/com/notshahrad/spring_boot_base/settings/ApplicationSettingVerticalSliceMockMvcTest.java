package com.notshahrad.spring_boot_base.settings;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "ADMIN")
class ApplicationSettingVerticalSliceMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsReadsAndUpdatesASettingThroughTheWebLayer() throws Exception {
        String key = "feature." + UUID.randomUUID();
        String createJson = "{\"key\":\"" + key + "\",\"value\":\"off\"}";

        mockMvc.perform(post("/api/settings").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.key").value(key))
                .andExpect(jsonPath("$.value").value("off"));

        mockMvc.perform(get("/api/settings/{key}", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value(key))
                .andExpect(jsonPath("$.value").value("off"));

        mockMvc.perform(put("/api/settings/{key}", key).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"on\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("on"));

        mockMvc.perform(get("/api/settings/{key}", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("on"));
    }

    @Test
    void invalidRequestReturnsFieldLevelValidationErrors() throws Exception {
        mockMvc.perform(post("/api/settings").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"\",\"value\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.key").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.value").isNotEmpty());
    }

    @Test
    void duplicateKeyReturnsBusinessErrorWithoutInternalDetails() throws Exception {
        String key = "feature." + UUID.randomUUID();
        String body = "{\"key\":\"" + key + "\",\"value\":\"value\"}";

        mockMvc.perform(post("/api/settings").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/settings").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Setting key already exists"));
    }

    @Test
    void missingSettingReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/settings/{key}", "missing." + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    @WithMockUser(roles = "USER")
    void nonAdminCannotAccessSettings() throws Exception {
        mockMvc.perform(get("/api/settings/{key}", "example"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    @WithAnonymousUser
    void anonymousUserCannotAccessSettings() throws Exception {
        mockMvc.perform(get("/api/settings/{key}", "example"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }
}
