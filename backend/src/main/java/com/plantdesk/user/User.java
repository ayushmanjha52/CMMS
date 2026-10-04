package com.plantdesk.user;

import com.plantdesk.security.Role;
import com.plantdesk.tenancy.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "users")
public class User extends TenantScopedEntity {

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    private Trade trade;

    @Enumerated(EnumType.STRING)
    private Shift shift;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected User() {}

    public User(String email, String passwordHash, String fullName, Role role, Trade trade, Shift shift, Instant now) {
        if (role == Role.TECHNICIAN && trade == null) {
            throw new IllegalArgumentException("A technician must have a trade");
        }
        this.email = email.trim().toLowerCase();
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
        this.trade = trade;
        this.shift = shift;
        this.createdAt = now;
    }

    public void deactivate() {
        this.active = false;
    }

    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public Role getRole() { return role; }
    public Trade getTrade() { return trade; }
    public Shift getShift() { return shift; }
    public boolean isActive() { return active; }
}
