package com.plantdesk.auth;

import com.plantdesk.tenancy.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends TenantScopedEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false, updatable = false)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by")
    private UUID replacedBy;

    protected RefreshToken() {}

    RefreshToken(UUID userId, UUID familyId, String tokenHash, Instant issuedAt, Instant expiresAt) {
        this.userId = userId;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    void markRotated(Instant now, UUID successorId) {
        this.usedAt = now;
        this.replacedBy = successorId;
    }

    boolean isUsed() { return usedAt != null; }
    boolean isRevoked() { return revokedAt != null; }
    boolean isExpired(Instant now) { return !now.isBefore(expiresAt); }

    UUID getUserId() { return userId; }
    UUID getFamilyId() { return familyId; }
}
