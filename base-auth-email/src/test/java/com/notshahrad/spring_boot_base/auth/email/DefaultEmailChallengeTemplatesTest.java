package com.notshahrad.spring_boot_base.auth.email;

import com.notshahrad.spring_boot_base.auth.email.EmailChallengeTemplates.Challenge;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultEmailChallengeTemplatesTest {

    @Test
    void rendersTheExpectedPurposeAndKeepsTheChallengeOutOfDiagnostics() {
        var templates = new DefaultEmailChallengeTemplates();
        var recipient = new LoginEmail("person@example.test");
        var signup = new Challenge(recipient, URI.create("https://app.example.test/verify-email#token=example-proof"));
        var reset = new Challenge(recipient, URI.create("https://app.example.test/reset-password#token=example-reset"));

        var signupEmail = templates.signup(signup);
        var resetEmail = templates.passwordReset(reset);
        assertEquals("Verify your email address", signupEmail.subject());
        assertEquals("Reset your password", resetEmail.subject());
        assertTrue(signupEmail.textBody().contains(signup.link().toString()));
        assertTrue(resetEmail.textBody().contains(reset.link().toString()));
        assertFalse(signup.toString().contains("example-proof"));
        assertFalse(resetEmail.toString().contains("example-reset"));
    }
}
