package com.notshahrad.spring_boot_base.auth.email;

public final class DefaultEmailChallengeTemplates implements EmailChallengeTemplates {

    @Override
    public Content signup(Challenge challenge) {
        return challengeMessage(challenge, "Verify your email address");
    }

    @Override
    public Content passwordReset(Challenge challenge) {
        return challengeMessage(challenge, "Reset your password");
    }

    private static Content challengeMessage(Challenge challenge, String subject) {
        return new Content(subject,
                subject + " using this single-use link:\n\n" + challenge.link(), null);
    }
}
