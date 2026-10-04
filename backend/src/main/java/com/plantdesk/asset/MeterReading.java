package com.plantdesk.asset;

import com.plantdesk.tenancy.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "meter_readings")
public class MeterReading extends TenantScopedEntity {

    @Column(name = "asset_id", nullable = false, updatable = false)
    private UUID assetId;

    @Column(name = "reading_minutes", nullable = false, updatable = false)
    private long readingMinutes;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @Column(name = "recorded_by", nullable = false, updatable = false)
    private UUID recordedBy;

    protected MeterReading() {}

    public MeterReading(UUID assetId, long readingMinutes, Instant recordedAt, UUID recordedBy) {
        this.assetId = assetId;
        this.readingMinutes = readingMinutes;
        this.recordedAt = recordedAt;
        this.recordedBy = recordedBy;
    }
}
