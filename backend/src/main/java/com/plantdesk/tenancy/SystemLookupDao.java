package com.plantdesk.tenancy;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The only code allowed to read across tenants, and only through narrow SECURITY DEFINER
 * functions defined in V1__tenancy_and_auth.sql. Each answers exactly one question needed
 * before a tenant can be known (login, token refresh, iterating tenants for the scheduler)
 * and returns the minimum needed to then switch into a normal tenant-scoped transaction.
 */
@Repository
public class SystemLookupDao {

    private final JdbcTemplate jdbc;

    public SystemLookupDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record UserCredentials(UUID userId, UUID tenantId, String passwordHash, String role, boolean active) {}

    public Optional<UserCredentials> findUserForLogin(String plantCode, String email) {
        return jdbc.query("select * from auth_lookup_user(?, ?)",
                (rs, i) -> new UserCredentials(
                        rs.getObject("user_id", UUID.class),
                        rs.getObject("tenant_id", UUID.class),
                        rs.getString("password_hash"),
                        rs.getString("role"),
                        rs.getBoolean("active")),
                plantCode, email).stream().findFirst();
    }

    public Optional<UUID> findTenantOfRefreshToken(String tokenHash) {
        return Optional.ofNullable(
                jdbc.queryForObject("select auth_refresh_token_tenant(?)", UUID.class, tokenHash));
    }

    public Optional<UUID> findTenantIdByCode(String code) {
        return Optional.ofNullable(
                jdbc.queryForObject("select system_tenant_id_by_code(?)", UUID.class, code));
    }

    public List<UUID> allTenantIds() {
        return jdbc.queryForList("select system_tenant_ids()", UUID.class);
    }
}
