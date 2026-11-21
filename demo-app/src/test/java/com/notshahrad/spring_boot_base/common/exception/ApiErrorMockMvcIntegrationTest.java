package com.notshahrad.spring_boot_base.common.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
@Import(ApiErrorMockMvcIntegrationTest.ProbeController.class)
class ApiErrorMockMvcIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void invalidRequestBodyReturnsFieldLevelApiError() throws Exception {
        mockMvc.perform(post("/test-api-errors/validate").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.name").value("Name is required"))
                .andExpect(jsonPath("$.fieldErrors.email").value("Email must be valid"))
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @Test
    void malformedJsonReturnsSafeBadRequest() throws Exception {
        mockMvc.perform(post("/test-api-errors/validate").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Malformed request body"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void missingResourceReturnsNotFound() throws Exception {
        mockMvc.perform(get("/test-api-errors/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("User not found with id: 123"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void businessErrorReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/test-api-errors/business"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Business rule violated"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void unexpectedErrorDoesNotExposeInternalMessage() throws Exception {
        mockMvc.perform(get("/test-api-errors/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    record ValidationPayload(
            @NotBlank(message = "Name is required") String name,
            @Email(message = "Email must be valid") String email
    ) {
    }

    @RestController
    static class ProbeController {

        @PostMapping("/test-api-errors/validate")
        public void validate(@Valid @RequestBody ValidationPayload payload) {
        }

        @GetMapping("/test-api-errors/not-found")
        public void notFound() {
            throw new ResourceNotFoundException("User", 123);
        }

        @GetMapping("/test-api-errors/business")
        public void business() {
            throw new BusinessException("Business rule violated");
        }

        @GetMapping("/test-api-errors/unexpected")
        public void unexpected() {
            throw new IllegalStateException("Internal implementation detail");
        }
    }
}
