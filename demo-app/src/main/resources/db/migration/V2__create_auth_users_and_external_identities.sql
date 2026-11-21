CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    email VARCHAR(320),
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    display_name VARCHAR(200),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by VARCHAR(100),
    version BIGINT NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    deleted_by VARCHAR(100)
);

CREATE TABLE external_identities (
    id UUID PRIMARY KEY,
    app_user_id UUID NOT NULL REFERENCES app_users (id),
    issuer VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by VARCHAR(100),
    version BIGINT NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    deleted_by VARCHAR(100),
    CONSTRAINT uq_external_identity_issuer_subject UNIQUE (issuer, subject)
);

CREATE INDEX ix_external_identities_user ON external_identities (app_user_id);
