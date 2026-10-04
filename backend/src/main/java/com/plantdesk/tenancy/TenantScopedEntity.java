package com.plantdesk.tenancy;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Version;
import org.hibernate.annotations.Filter;

import java.util.UUID;

/**
 * Base for every row that belongs to a tenant.
 *
 * <p>The tenant id is stamped from {@link TenantContext} on insert and is not updatable.
 * There is deliberately no setter: no code path, and no request body, can choose which
 * tenant a row is written to.
 *
 * <p>UUID keys rather than sequences: sequential ids leak volume ("we are work order 41 of
 * this plant") and invite enumeration across tenants.
 */
@MappedSuperclass
@Filter(name = TenantFilters.TENANT, condition = "tenant_id = :" + TenantFilters.TENANT_PARAM)
public abstract class TenantScopedEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    // Optimistic locking, and Spring Data uses "version == null" to decide persist vs merge.
    @Version
    private Long version;

    @PrePersist
    void bindTenant() {
        UUID current = TenantContext.require().tenantId();
        if (tenantId != null && !tenantId.equals(current)) {
            throw new IllegalStateException("Entity already bound to a different tenant");
        }
        tenantId = current;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public Long getVersion() {
        return version;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TenantScopedEntity other)) return false;
        return id.equals(other.id);
    }

    @Override
    public final int hashCode() {
        return id.hashCode();
    }
}
