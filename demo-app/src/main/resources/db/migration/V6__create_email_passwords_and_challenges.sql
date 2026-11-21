CREATE TABLE email_passwords (
    user_id UUID PRIMARY KEY REFERENCES email_login_identities (user_id) ON DELETE CASCADE,
    password_hash VARCHAR(255) NOT NULL,
    credential_version BIGINT NOT NULL CHECK (credential_version > 0)
);

CREATE TABLE email_auth_challenges (
    purpose VARCHAR(12) NOT NULL,
    address VARCHAR(254) COLLATE "C" NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    owner_id UUID REFERENCES email_login_identities (user_id) ON DELETE CASCADE,
    credential_version BIGINT,
    resend_after TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (purpose, address),
    CHECK (expires_at > resend_after),
    CHECK ((purpose = 'SIGNUP' AND owner_id IS NULL AND credential_version IS NULL)
        OR (purpose = 'RESET' AND owner_id IS NOT NULL AND credential_version IS NOT NULL AND credential_version > 0))
);

CREATE INDEX ix_email_auth_challenges_expiry ON email_auth_challenges (expires_at);
