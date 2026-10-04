-- =============================================================================
-- V4: preventive maintenance schedules with dual triggers.
-- =============================================================================

CREATE TABLE pm_schedules (
    id                        uuid PRIMARY KEY,
    tenant_id                 uuid         NOT NULL REFERENCES tenants (id),
    asset_id                  uuid         NOT NULL,
    title                     varchar(200) NOT NULL,
    instructions              text,
    trade                     varchar(32)  NOT NULL CHECK (trade IN ('ELECTRICAL', 'MECHANICAL', 'INSTRUMENTATION')),
    priority                  varchar(16)  NOT NULL CHECK (priority IN ('EMERGENCY', 'HIGH', 'MEDIUM', 'LOW')),
    -- Either or both. With both, whichever is reached first triggers the PM: a pump that
    -- runs 24x7 hits its hours long before the calendar; a standby pump hits the calendar.
    interval_days             integer CHECK (interval_days > 0),
    interval_running_minutes  bigint CHECK (interval_running_minutes > 0),
    last_done_at              timestamptz  NOT NULL,
    last_done_running_minutes bigint       NOT NULL DEFAULT 0 CHECK (last_done_running_minutes >= 0),
    generated_count           integer      NOT NULL DEFAULT 0,
    active                    boolean      NOT NULL DEFAULT true,
    created_at                timestamptz  NOT NULL DEFAULT now(),
    version                   bigint       NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, asset_id) REFERENCES assets (tenant_id, id),
    CONSTRAINT has_a_trigger CHECK (interval_days IS NOT NULL OR interval_running_minutes IS NOT NULL)
);
CREATE INDEX pm_schedules_asset_idx ON pm_schedules (tenant_id, asset_id);

ALTER TABLE work_orders
    ADD CONSTRAINT work_orders_pm_schedule_fk
        FOREIGN KEY (tenant_id, pm_schedule_id) REFERENCES pm_schedules (tenant_id, id);

-- The duplicate guard that does not depend on application code being correct:
-- occurrence N of a schedule can exist at most once, even if two scheduler instances race.
CREATE UNIQUE INDEX work_orders_pm_occurrence_uk
    ON work_orders (pm_schedule_id, pm_sequence)
    WHERE pm_schedule_id IS NOT NULL;

ALTER TABLE pm_schedules ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON pm_schedules
    USING (tenant_id = app_current_tenant())
    WITH CHECK (tenant_id = app_current_tenant());
