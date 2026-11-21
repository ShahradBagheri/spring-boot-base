package com.notshahrad.spring_boot_base.mail;

public record EmailMessage(String recipient, String subject, String textBody, String htmlBody) {

    public EmailMessage {
        requireHeader(recipient, "recipient");
        requireHeader(subject, "subject");
        if (recipient.chars().anyMatch(Character::isWhitespace) || recipient.contains(",")
                || recipient.contains(";") || recipient.indexOf('@') <= 0
                || recipient.indexOf('@') != recipient.lastIndexOf('@') || recipient.endsWith("@")) {
            throw new IllegalArgumentException("Provide a single recipient email address");
        }
        if ((textBody == null || textBody.isBlank()) && (htmlBody == null || htmlBody.isBlank())) {
            throw new IllegalArgumentException("Provide a text or HTML email body");
        }
    }

    private static void requireHeader(String value, String field) {
        if (value == null || value.isBlank() || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Provide a valid email " + field);
        }
    }

    @Override
    public String toString() {
        return "EmailMessage[redacted]";
    }
}
