package com.plantdesk.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * A subscribing plant. Not a TenantScopedEntity (it IS the tenant); RLS on the tenants
 * table restricts a session to its own row.
 */
@Entity
@Table(name = "tenants")
public class Tenant {

    @Id
    private UUID id;

    // Short plant code typed at login, e.g. "DEMO". Plants already have these.
    @Column(nullable = false, updatable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private Long version;

    protected Tenant() {}

    public Tenant(UUID id, String code, String name, Instant createdAt) {
        this.id = id;
        this.code = code.toUpperCase();
        this.name = name;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
}
