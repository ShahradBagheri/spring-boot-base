CREATE TABLE refresh_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users (id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    rotated_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_refresh_session_expiry CHECK (expires_at > created_at)
);

CREATE INDEX ix_refresh_sessions_user ON refresh_sessions (user_id);
CREATE INDEX ix_refresh_sessions_expiry ON refresh_sessions (expires_at);

CREATE TABLE refresh_credentials (
    token_hash VARCHAR(64) PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES refresh_sessions (id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_refresh_credential_hash_length CHECK (CHAR_LENGTH(token_hash) = 64)
);

CREATE INDEX ix_refresh_credentials_session ON refresh_credentials (session_id);
