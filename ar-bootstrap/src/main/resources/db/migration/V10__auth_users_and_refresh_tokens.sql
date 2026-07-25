-- ============================================================================
-- Production auth: app users, roles, refresh tokens (rotation + revocation)
-- Note: no RLS on auth tables — login/refresh run before tenant GUC is set;
-- app-layer filters enforce tenant on admin user APIs.
-- ============================================================================

CREATE TABLE ar_app_user (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES ar_tenant(id),
    email           VARCHAR(320) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    display_name    VARCHAR(255) NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_login_at   TIMESTAMPTZ,
    CONSTRAINT uq_app_user_tenant_email UNIQUE (tenant_id, email),
    CONSTRAINT uq_app_user_email_global UNIQUE (email),
    CONSTRAINT chk_app_user_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX idx_app_user_tenant ON ar_app_user(tenant_id);
CREATE INDEX idx_app_user_email_lower ON ar_app_user (lower(email));

CREATE TABLE ar_app_user_role (
    user_id     UUID NOT NULL REFERENCES ar_app_user(id) ON DELETE CASCADE,
    role        VARCHAR(64) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT chk_app_user_role CHECK (role IN (
        'AR_CLERK', 'AR_CONTROLLER', 'AR_AUDITOR', 'TENANT_ADMIN'
    ))
);

CREATE TABLE ar_refresh_token (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES ar_app_user(id) ON DELETE CASCADE,
    tenant_id       UUID NOT NULL REFERENCES ar_tenant(id),
    token_hash      VARCHAR(64) NOT NULL,
    family_id       UUID NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ,
    replaced_by     UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at    TIMESTAMPTZ,
    user_agent      VARCHAR(512),
    ip_address      VARCHAR(64),
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_token_user ON ar_refresh_token(user_id);
CREATE INDEX idx_refresh_token_family ON ar_refresh_token(family_id);
CREATE INDEX idx_refresh_token_expires ON ar_refresh_token(expires_at);