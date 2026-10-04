-- =============================================================================
-- V2: asset hierarchy, nameplate, criticality, running-hours meter.
-- =============================================================================

CREATE TABLE assets (
    id                 uuid PRIMARY KEY,
    tenant_id          uuid         NOT NULL REFERENCES tenants (id),
    parent_id          uuid,
    level              varchar(16)  NOT NULL
        CHECK (level IN ('PLANT', 'AREA', 'LINE', 'MACHINE', 'COMPONENT')),
    -- Last segment of the tag, e.g. MTR-114. Generated, never typed in by hand.
    code               varchar(16)  NOT NULL,
    -- Materialised path, e.g. PLT-01/AREA-03/LN-02/MTR-114. Stored rather than computed
    -- because it is shown on every row of every list and is what people search by.
    tag                varchar(200) NOT NULL,
    name               varchar(200) NOT NULL,
    make               varchar(100),
    model              varchar(100),
    rating             varchar(100),
    serial_number      varchar(100),
    criticality        varchar(1)   NOT NULL DEFAULT 'B' CHECK (criticality IN ('A', 'B', 'C')),
    running_minutes    bigint       NOT NULL DEFAULT 0 CHECK (running_minutes >= 0),
    running_updated_at timestamptz,
    commissioned_on    date,
    in_service         boolean      NOT NULL DEFAULT true,
    created_at         timestamptz  NOT NULL DEFAULT now(),
    version            bigint       NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, tag),
    FOREIGN KEY (tenant_id, parent_id) REFERENCES assets (tenant_id, id),
    CONSTRAINT only_plants_are_roots CHECK ((level = 'PLANT') = (parent_id IS NULL))
);
-- varchar_pattern_ops makes "tag LIKE 'PLT-01/AREA-03/%'" (subtree) an index range scan.
CREATE INDEX assets_tag_prefix_idx ON assets (tenant_id, tag varchar_pattern_ops);
CREATE INDEX assets_parent_idx ON assets (tenant_id, parent_id);

-- Per-tenant counters for tag numbers. Upserted atomically (INSERT .. ON CONFLICT ..
-- RETURNING), so two admins adding motors at the same moment cannot both get MTR-115.
CREATE TABLE asset_code_counters (
    tenant_id  uuid        NOT NULL REFERENCES tenants (id),
    prefix     varchar(8)  NOT NULL,
    last_value integer     NOT NULL,
    PRIMARY KEY (tenant_id, prefix)
);

-- Hour-meter readings. Kept as history (not just the latest value) because a meter that
-- jumps or stalls is itself a fault indication, and PM-by-hours needs an audit trail.
CREATE TABLE meter_readings (
    id              uuid PRIMARY KEY,
    tenant_id       uuid        NOT NULL REFERENCES tenants (id),
    asset_id        uuid        NOT NULL,
    reading_minutes bigint      NOT NULL CHECK (reading_minutes >= 0),
    recorded_at     timestamptz NOT NULL,
    recorded_by     uuid        NOT NULL,
    version         bigint      NOT NULL DEFAULT 0,
    FOREIGN KEY (tenant_id, asset_id) REFERENCES assets (tenant_id, id),
    FOREIGN KEY (tenant_id, recorded_by) REFERENCES users (tenant_id, id)
);
CREATE INDEX meter_readings_asset_idx ON meter_readings (tenant_id, asset_id, recorded_at);

ALTER TABLE assets ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON assets
    USING (tenant_id = app_current_tenant())
    WITH CHECK (tenant_id = app_current_tenant());

ALTER TABLE asset_code_counters ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON asset_code_counters
    USING (tenant_id = app_current_tenant())
    WITH CHECK (tenant_id = app_current_tenant());

ALTER TABLE meter_readings ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON meter_readings
    USING (tenant_id = app_current_tenant())
    WITH CHECK (tenant_id = app_current_tenant());
