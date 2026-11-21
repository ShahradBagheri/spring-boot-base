CREATE TABLE phone_otp_challenges (
    phone_number VARCHAR(16) COLLATE "C" NOT NULL CHECK (phone_number ~ '^[+][1-9][0-9]{1,14}$'),
    purpose VARCHAR(8) NOT NULL CHECK (purpose IN ('SIGNUP', 'LOGIN')),
    challenge_id UUID NOT NULL UNIQUE,
    verifier BYTEA NOT NULL CHECK (octet_length(verifier) = 32),
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL CHECK (expires_at > issued_at),
    resend_after TIMESTAMP WITH TIME ZONE NOT NULL CHECK (resend_after > issued_at),
    failed_attempts INTEGER NOT NULL DEFAULT 0 CHECK (failed_attempts >= 0),
    consumed BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (phone_number, purpose)
);
CREATE INDEX ix_phone_otp_challenges_expiry ON phone_otp_challenges (expires_at);
