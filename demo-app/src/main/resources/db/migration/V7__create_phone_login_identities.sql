CREATE TABLE phone_login_identities (
    user_id UUID PRIMARY KEY REFERENCES app_users (id),
    phone_number VARCHAR(16) COLLATE "C" NOT NULL,
    verified_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_phone_login_number UNIQUE (phone_number),
    CONSTRAINT ck_phone_login_e164 CHECK (phone_number ~ '^[+][1-9][0-9]{1,14}$')
);
