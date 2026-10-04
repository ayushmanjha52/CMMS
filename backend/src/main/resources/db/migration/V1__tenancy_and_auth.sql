-- =============================================================================
-- V1: tenancy, users, refresh tokens, and the row-level security foundation.
--
-- Runs as the schema OWNER. The application connects as ${app_role}: not the owner,
-- not a superuser, no BYPASSRLS. That is what makes the policies below binding on it.
-- RLS is ENABLEd, not FORCEd, on purpose: the owner must bypass it so the narrow
-- SECURITY DEFINER lookup functions at the bottom of this file can work before a
-- tenant is known (login, refresh). The app role can never become the owner.
-- =============================================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${app_role}') THEN
        EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE',
                       '${app_role}', '${app_password}');
    END IF;
END
$$;

GRANT USAGE ON SCHEMA public TO "${app_role}";
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO "${app_role}";
-- New functions are not executable by anyone unless granted explicitly below.
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;

-- -----------------------------------------------------------------------------
-- Session context, set per transaction by TenantAwareJpaTransactionManager.
-- NULLIF guards the cast: once set_config(..., true) has been used on a pooled
-- connection, the variable reverts to '' (not NULL) after commit, and ''::uuid errors.
-- -----------------------------------------------------------------------------
CREATE FUNCTION app_current_tenant() RETURNS uuid
    LANGUAGE sql STABLE AS
$$ SELECT NULLIF(current_setting('app.tenant_id', true), '')::uuid $$;

CREATE FUNCTION app_current_user() RETURNS uuid
    LANGUAGE sql STABLE AS
$$ SELECT NULLIF(current_setting('app.user_id', true), '')::uuid $$;

CREATE FUNCTION app_current_role() RETURNS text
    LANGUAGE sql STABLE AS
$$ SELECT NULLIF(current_setting('app.role', true), '') $$;

GRANT EXECUTE ON FUNCTION app_current_tenant(), app_current_user(), app_current_role() TO "${app_role}";

-- -----------------------------------------------------------------------------
-- Tables
-- -----------------------------------------------------------------------------
CREATE TABLE tenants (
    id         uuid PRIMARY KEY,
    code       varchar(32)  NOT NULL UNIQUE CHECK (code = upper(code)),
    name       varchar(200) NOT NULL,
    created_at timestamptz  NOT NULL DEFAULT now(),
    version    bigint       NOT NULL DEFAULT 0
);

CREATE TABLE users (
    id            uuid PRIMARY KEY,
    tenant_id     uuid         NOT NULL REFERENCES tenants (id),
    email         varchar(254) NOT NULL CHECK (email = lower(email)),
    password_hash varchar(100) NOT NULL,
    full_name     varchar(200) NOT NULL,
    role          varchar(32)  NOT NULL
        CHECK (role IN ('PLANT_ADMIN', 'MAINTENANCE_MANAGER', 'TECHNICIAN', 'VIEWER')),
    trade         varchar(32) CHECK (trade IN ('ELECTRICAL', 'MECHANICAL', 'INSTRUMENTATION')),
    shift         varchar(16) CHECK (shift IN ('A', 'B', 'C', 'GENERAL')),
    active        boolean      NOT NULL DEFAULT true,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    version       bigint       NOT NULL DEFAULT 0,
    -- Composite key target: lets child tables reference (tenant_id, id) so a row in
    -- tenant A can never point at a user in tenant B, even via a raw INSERT.
    -- (Foreign-key checks bypass RLS, so RLS alone would not stop that.)
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, email),
    CONSTRAINT technician_has_trade CHECK (role <> 'TECHNICIAN' OR trade IS NOT NULL)
);

CREATE TABLE refresh_tokens (
    id          uuid PRIMARY KEY,
    tenant_id   uuid        NOT NULL REFERENCES tenants (id),
    user_id     uuid        NOT NULL,
    -- One family per login. Rotation keeps the family; reuse of a rotated token
    -- revokes every token in the family (the attacker's and the victim's).
    family_id   uuid        NOT NULL,
    -- SHA-256 of the token, never the token itself. A database dump yields nothing usable.
    token_hash  varchar(64) NOT NULL UNIQUE,
    issued_at   timestamptz NOT NULL,
    expires_at  timestamptz NOT NULL,
    used_at     timestamptz,
    revoked_at  timestamptz,
    replaced_by uuid,
    version     bigint      NOT NULL DEFAULT 0,
    FOREIGN KEY (tenant_id, user_id) REFERENCES users (tenant_id, id)
);
CREATE INDEX refresh_tokens_family_idx ON refresh_tokens (family_id);

-- -----------------------------------------------------------------------------
-- Row-level security
-- -----------------------------------------------------------------------------
ALTER TABLE tenants ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_self ON tenants
    USING (id = app_current_tenant())
    WITH CHECK (id = app_current_tenant());

ALTER TABLE users ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON users
    USING (tenant_id = app_current_tenant())
    WITH CHECK (tenant_id = app_current_tenant());

ALTER TABLE refresh_tokens ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON refresh_tokens
    USING (tenant_id = app_current_tenant())
    WITH CHECK (tenant_id = app_current_tenant());

-- -----------------------------------------------------------------------------
-- Narrow cross-tenant lookups. SECURITY DEFINER = runs as the owner, who bypasses RLS.
-- Each returns the minimum needed to then open a normal tenant-scoped transaction.
-- search_path is pinned so a caller cannot shadow a table with their own object.
-- -----------------------------------------------------------------------------
CREATE FUNCTION auth_lookup_user(p_plant_code text, p_email text)
    RETURNS TABLE (user_id uuid, tenant_id uuid, password_hash text, role text, active boolean)
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path = public, pg_temp AS
$$
SELECT u.id, u.tenant_id, u.password_hash::text, u.role::text, u.active
FROM users u
         JOIN tenants t ON t.id = u.tenant_id
WHERE t.code = upper(p_plant_code)
  AND u.email = lower(p_email)
$$;

CREATE FUNCTION auth_refresh_token_tenant(p_token_hash text) RETURNS uuid
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path = public, pg_temp AS
$$ SELECT tenant_id FROM refresh_tokens WHERE token_hash = p_token_hash $$;

CREATE FUNCTION system_tenant_ids() RETURNS SETOF uuid
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path = public, pg_temp AS
$$ SELECT id FROM tenants ORDER BY code $$;

CREATE FUNCTION system_tenant_id_by_code(p_code text) RETURNS uuid
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path = public, pg_temp AS
$$ SELECT id FROM tenants WHERE code = upper(p_code) $$;

GRANT EXECUTE ON FUNCTION auth_lookup_user(text, text),
    auth_refresh_token_tenant(text),
    system_tenant_ids(),
    system_tenant_id_by_code(text) TO "${app_role}";
