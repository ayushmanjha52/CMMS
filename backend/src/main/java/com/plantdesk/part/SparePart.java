package com.plantdesk.part;

import com.plantdesk.tenancy.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Entity
@Table(name = "spare_parts")
public class SparePart extends TenantScopedEntity {

    @Column(name = "part_number", nullable = false, updatable = false)
    private String partNumber;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UnitOfMeasure unit;

    // BigDecimal for quantity and money. A double here turns 0.1 L + 0.2 L into
    // 0.30000000000000004 L and the store ledger never balances.
    @Column(name = "stock_qty", nullable = false, precision = 12, scale = 3)
    private BigDecimal stockQty = BigDecimal.ZERO;

    @Column(name = "reorder_point", nullable = false, precision = 12, scale = 3)
    private BigDecimal reorderPoint = BigDecimal.ZERO;

    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "bin_location")
    private String binLocation;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SparePart() {}

    public SparePart(String partNumber, String description, UnitOfMeasure unit, BigDecimal unitCost,
                     BigDecimal reorderPoint, String binLocation, Instant now) {
        this.partNumber = partNumber.trim().toUpperCase();
        this.description = description;
        this.unit = unit;
        this.unitCost = unitCost.setScale(2, RoundingMode.HALF_UP);
        this.reorderPoint = reorderPoint.setScale(3, RoundingMode.HALF_UP);
        this.binLocation = binLocation;
        this.createdAt = now;
    }

    /** Goods receipt. Unit cost moves to the latest purchase price when one is given. */
    public void receive(BigDecimal quantity, BigDecimal newUnitCost) {
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException("Received quantity must be positive");
        }
        this.stockQty = stockQty.add(quantity).setScale(3, RoundingMode.HALF_UP);
        if (newUnitCost != null) {
            this.unitCost = newUnitCost.setScale(2, RoundingMode.HALF_UP);
        }
    }

    public boolean isBelowReorderPoint() {
        return stockQty.compareTo(reorderPoint) <= 0;
    }

    public String getPartNumber() { return partNumber; }
    public String getDescription() { return description; }
    public UnitOfMeasure getUnit() { return unit; }
    public BigDecimal getStockQty() { return stockQty; }
    public BigDecimal getReorderPoint() { return reorderPoint; }
    public BigDecimal getUnitCost() { return unitCost; }
    public String getBinLocation() { return binLocation; }
}
