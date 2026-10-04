-- =============================================================================
-- V5: analytics. MTBF/MTTR read breakdowns by asset over a time window; this partial
-- index covers exactly that access path and nothing else.
-- =============================================================================

CREATE INDEX work_orders_breakdown_window_idx
    ON work_orders (tenant_id, asset_id, downtime_start)
    WHERE type = 'BREAKDOWN';
