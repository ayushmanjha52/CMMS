package com.plantdesk.workorder;

import com.plantdesk.tenancy.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "part_consumptions")
public class PartConsumption extends TenantScopedEntity {

    @Column(name = "work_order_id", nullable = false, updatable = false)
    private UUID workOrderId;

    @Column(name = "spare_part_id", nullable = false, updatable = false)
    private UUID sparePartId;

    @Column(nullable = false, updatable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_cost", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "consumed_at", nullable = false, updatable = false)
    private Instant consumedAt;

    @Column(name = "consumed_by", nullable = false, updatable = false)
    private UUID consumedBy;

    protected PartConsumption() {}

    public PartConsumption(UUID workOrderId, UUID sparePartId, BigDecimal quantity, BigDecimal unitCost,
                           Instant consumedAt, UUID consumedBy) {
        this.workOrderId = workOrderId;
        this.sparePartId = sparePartId;
        this.quantity = quantity.setScale(3, RoundingMode.HALF_UP);
        this.unitCost = unitCost;
        this.consumedAt = consumedAt;
        this.consumedBy = consumedBy;
    }

    /** Rounded once, at the line level, to paise — the way an invoice line is rounded. */
    public BigDecimal lineCost() {
        return quantity.multiply(unitCost).setScale(2, RoundingMode.HALF_UP);
    }

    public UUID getSparePartId() { return sparePartId; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitCost() { return unitCost; }
    public Instant getConsumedAt() { return consumedAt; }
}
