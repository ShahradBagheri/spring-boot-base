CREATE TABLE email_login_identities (
    user_id UUID PRIMARY KEY REFERENCES app_users (id),
    address VARCHAR(254) COLLATE "C" NOT NULL,
    verified_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_email_login_address CHECK (
        LENGTH(address) - LENGTH(REPLACE(address, '@', '')) = 1
        AND POSITION('@' IN address) BETWEEN 2 AND 65
        AND SPLIT_PART(address, '@', 2) <> ''
        AND SPLIT_PART(address, '@', 2) = LOWER(SPLIT_PART(address, '@', 2))
    )
);

CREATE UNIQUE INDEX uq_email_login_verified_address
    ON email_login_identities (address)
    WHERE verified_at IS NOT NULL;
