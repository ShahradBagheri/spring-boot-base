package com.notshahrad.spring_boot_base.auth.email;

import java.net.URI;
import java.util.Objects;

public interface EmailChallengeTemplates {

    Content signup(Challenge challenge);

    Content passwordReset(Challenge challenge);

    record Content(String subject, String textBody, String htmlBody) {
        @Override
        public String toString() {
            return "Content[redacted]";
        }
    }

    record Challenge(LoginEmail recipient, URI link) {
        public Challenge {
            Objects.requireNonNull(recipient, "recipient");
            Objects.requireNonNull(link, "link");
        }

        @Override
        public String toString() {
            return "Challenge[redacted]";
        }
    }
}
