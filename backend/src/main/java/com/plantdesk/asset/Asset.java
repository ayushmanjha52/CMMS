package com.plantdesk.asset;

import com.plantdesk.common.ConflictException;
import com.plantdesk.tenancy.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "assets")
public class Asset extends TenantScopedEntity {

    // Plain id, not a @ManyToOne: the tree is assembled in memory from one flat query, so a
    // lazy parent proxy would only be a way to accidentally trigger N+1 loads.
    @Column(name = "parent_id", updatable = false)
    private UUID parentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AssetLevel level;

    @Column(nullable = false, updatable = false)
    private String code;

    // No re-parenting: moving equipment changes its tag, and work order history is filed
    // under the old tag. Plants decommission and re-register instead.
    @Column(nullable = false, updatable = false)
    private String tag;

    @Column(nullable = false)
    private String name;

    private String make;
    private String model;
    private String rating;

    @Column(name = "serial_number")
    private String serialNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Criticality criticality = Criticality.B;

    // Integer minutes, never a floating-point hour count.
    @Column(name = "running_minutes", nullable = false)
    private long runningMinutes;

    @Column(name = "running_updated_at")
    private Instant runningUpdatedAt;

    @Column(name = "commissioned_on")
    private LocalDate commissionedOn;

    @Column(name = "in_service", nullable = false)
    private boolean inService = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Asset() {}

    Asset(Asset parent, AssetLevel level, String code, String name, Instant now) {
        if (parent == null && level != AssetLevel.PLANT) {
            throw new IllegalArgumentException("Only a PLANT can be a root asset");
        }
        if (parent != null && !parent.level.canParent(level)) {
            throw new IllegalArgumentException("A " + level + " cannot be placed under a " + parent.level);
        }
        this.parentId = parent == null ? null : parent.getId();
        this.level = level;
        this.code = code;
        this.tag = parent == null ? code : parent.tag + "/" + code;
        this.name = name;
        this.createdAt = now;
    }

    public void updateNameplate(String name, String make, String model, String rating, String serialNumber,
                                Criticality criticality, LocalDate commissionedOn, boolean inService) {
        this.name = name;
        this.make = make;
        this.model = model;
        this.rating = rating;
        this.serialNumber = serialNumber;
        this.criticality = criticality;
        this.commissionedOn = commissionedOn;
        this.inService = inService;
    }

    /**
     * Hour meters only count up. A lower reading means a typo or a replaced meter, and either
     * way silently accepting it would push every running-hours PM out by the difference.
     */
    public void recordRunningMinutes(long reading, Instant at) {
        if (reading < runningMinutes) {
            throw new ConflictException("Hour-meter reading " + (reading / 60) + " h is below the current "
                    + (runningMinutes / 60) + " h. Meters do not run backwards; if the meter was replaced, record that separately.");
        }
        this.runningMinutes = reading;
        this.runningUpdatedAt = at;
    }

    /** Start of the observation window for reliability: commissioning date if known, else registration. */
    public Instant inServiceSince() {
        return commissionedOn != null ? commissionedOn.atStartOfDay(java.time.ZoneOffset.UTC).toInstant() : createdAt;
    }

    public UUID getParentId() { return parentId; }
    public AssetLevel getLevel() { return level; }
    public String getCode() { return code; }
    public String getTag() { return tag; }
    public String getName() { return name; }
    public String getMake() { return make; }
    public String getModel() { return model; }
    public String getRating() { return rating; }
    public String getSerialNumber() { return serialNumber; }
    public Criticality getCriticality() { return criticality; }
    public long getRunningMinutes() { return runningMinutes; }
    public Instant getRunningUpdatedAt() { return runningUpdatedAt; }
    public LocalDate getCommissionedOn() { return commissionedOn; }
    public boolean isInService() { return inService; }
    public Instant getCreatedAt() { return createdAt; }
}
