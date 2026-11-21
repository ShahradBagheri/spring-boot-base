CREATE TABLE guest_drafts (
    id UUID PRIMARY KEY,
    account_id UUID UNIQUE REFERENCES app_users (id),
    expires_at TIMESTAMP WITH TIME ZONE,
    content VARCHAR(2000) NOT NULL,
    CHECK ((account_id IS NULL AND expires_at IS NOT NULL) OR (account_id IS NOT NULL AND expires_at IS NULL))
);

CREATE INDEX ix_guest_drafts_expiry ON guest_drafts (expires_at, id) WHERE account_id IS NULL;
