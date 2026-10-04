package com.plantdesk.workorder;

import com.plantdesk.tenancy.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "labour_entries")
public class LabourEntry extends TenantScopedEntity {

    @Column(name = "work_order_id", nullable = false, updatable = false)
    private UUID workOrderId;

    @Column(name = "technician_id", nullable = false, updatable = false)
    private UUID technicianId;

    @Column(nullable = false, updatable = false)
    private int minutes;

    @Column(name = "work_date", nullable = false, updatable = false)
    private LocalDate workDate;

    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private java.time.Instant createdAt;

    protected LabourEntry() {}

    public LabourEntry(UUID workOrderId, UUID technicianId, int minutes, LocalDate workDate, String note,
                       java.time.Instant now) {
        if (minutes <= 0 || minutes > 1440) {
            throw new IllegalArgumentException("Labour must be between 1 minute and 24 hours per entry");
        }
        this.workOrderId = workOrderId;
        this.technicianId = technicianId;
        this.minutes = minutes;
        this.workDate = workDate;
        this.note = note;
        this.createdAt = now;
    }

    public UUID getTechnicianId() { return technicianId; }
    public int getMinutes() { return minutes; }
    public LocalDate getWorkDate() { return workDate; }
    public String getNote() { return note; }
}
