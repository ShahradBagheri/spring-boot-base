CREATE UNIQUE INDEX uq_refresh_credentials_unused_session
    ON refresh_credentials (session_id)
    WHERE consumed_at IS NULL;

ALTER TABLE refresh_credentials
    ADD CONSTRAINT ck_refresh_credentials_sha256 CHECK (token_hash ~ '^[0-9a-f]{64}$');
