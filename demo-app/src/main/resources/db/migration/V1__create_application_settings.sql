CREATE TABLE application_settings (
    id UUID PRIMARY KEY,
    setting_key VARCHAR(100) NOT NULL,
    setting_value VARCHAR(2048) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by VARCHAR(100),
    version BIGINT NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    deleted_by VARCHAR(100),
    CONSTRAINT uq_application_settings_setting_key UNIQUE (setting_key)
);
