package com.plantdesk.security;

import com.plantdesk.tenancy.TenantScope;

import java.util.UUID;

/** The principal reconstructed from a validated access token. No database hit per request. */
public record AuthenticatedUser(UUID userId, UUID tenantId, Role role, String email, String fullName) {

    public TenantScope toTenantScope() {
        return new TenantScope(tenantId, userId, role);
    }

    public boolean is(Role r) {
        return role == r;
    }
}
