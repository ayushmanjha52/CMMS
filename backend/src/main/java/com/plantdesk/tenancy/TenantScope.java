package com.plantdesk.tenancy;

import com.plantdesk.security.Role;

import java.util.Objects;
import java.util.UUID;

/**
 * Who is acting, and on behalf of which tenant. Built only from a validated JWT (or, for
 * background jobs and the pre-authentication auth flows, from a server-side lookup) —
 * never from anything the client sends in a parameter or body.
 *
 * @param userId null for system work (PM generation, token refresh before a user is loaded)
 * @param role   null for system work
 */
public record TenantScope(UUID tenantId, UUID userId, Role role) {

    public TenantScope {
        Objects.requireNonNull(tenantId, "tenantId");
    }

    public static TenantScope system(UUID tenantId) {
        return new TenantScope(tenantId, null, null);
    }

    /** Technicians only ever see work orders assigned to them — a row rule, not an endpoint rule. */
    public boolean restrictedToOwnWorkOrders() {
        return role == Role.TECHNICIAN;
    }

    public String roleNameForDatabase() {
        return role == null ? "SYSTEM" : role.name();
    }
}
