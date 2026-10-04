package com.plantdesk.isolation;

import com.plantdesk.support.AbstractIntegrationTest;
import com.plantdesk.support.TestData.Plant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Second line of defence, tested alone: raw JDBC on the application's own connection pool.
 * No Hibernate, no filter — only PostgreSQL policies stand between the query and the data.
 * This is what protects you from the native query someone writes in two years.
 */
class RowLevelSecurityIT extends AbstractIntegrationTest {

    @Autowired
    DataSource dataSource;

    Plant a;
    Plant b;
    UUID bWorkOrder;
    UUID aTech1Order;

    @BeforeEach
    void setUp() {
        a = data.newPlant();
        b = data.newPlant();
        bWorkOrder = data.openBreakdown(b, b.machineId(), b.tech1());
        aTech1Order = data.openBreakdown(a, a.machineId(), a.tech1());
        data.openBreakdown(a, a.machineId(), a.tech2());
    }

    @Test
    void applicationRole_isNotSuperuser_andCannotBypassRls() throws SQLException {
        try (Connection c = dataSource.getConnection();
             ResultSet rs = c.createStatement().executeQuery(
                     "select rolsuper, rolbypassrls, current_user from pg_roles where rolname = current_user")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(3)).isEqualTo(APP_ROLE);
            assertThat(rs.getBoolean(1)).as("superuser bypasses RLS").isFalse();
            assertThat(rs.getBoolean(2)).as("BYPASSRLS bypasses RLS").isFalse();
        }
    }

    @Test
    void withNoTenantSet_everyTenantTableReadsEmpty() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            for (String table : new String[]{"tenants", "users", "assets", "work_orders", "spare_parts", "refresh_tokens"}) {
                assertThat(count(c, "select count(*) from " + table)).as(table).isZero();
            }
        }
    }

    @Test
    void asTenantA_tenantB_rowIsInvisible_byDirectId() throws SQLException {
        inTenantTransaction(a.tenantId(), null, "PLANT_ADMIN", c -> {
            assertThat(count(c, "select count(*) from work_orders where id = '" + bWorkOrder + "'")).isZero();
            assertThat(count(c, "select count(*) from work_orders")).isEqualTo(2);
        });
    }

    @Test
    void asTenantA_updatingTenantB_rowTouchesNothing() throws SQLException {
        inTenantTransaction(a.tenantId(), null, "PLANT_ADMIN", c -> {
            try (PreparedStatement ps = c.prepareStatement("update work_orders set title = 'pwned' where id = ?")) {
                ps.setObject(1, bWorkOrder);
                assertThat(ps.executeUpdate()).isZero();
            }
        });
    }

    @Test
    void asTenantA_insertingARowForTenantB_isRejected() {
        assertThatThrownBy(() -> inTenantTransaction(a.tenantId(), null, "PLANT_ADMIN", c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "insert into spare_parts (id, tenant_id, part_number, description, unit, unit_cost) values (?, ?, 'X', 'x', 'EA', 1)")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, b.tenantId());
                ps.executeUpdate();
            }
        })).isInstanceOf(SQLException.class).hasMessageContaining("row-level security");
    }

    @Test
    void technicianRole_seesOnlyAssignedWorkOrders_atTheDatabase() throws SQLException {
        inTenantTransaction(a.tenantId(), a.tech1().getId(), "TECHNICIAN", c -> {
            assertThat(count(c, "select count(*) from work_orders")).isEqualTo(1);
            assertThat(count(c, "select count(*) from work_orders where id = '" + aTech1Order + "'")).isEqualTo(1);
        });
    }

    @Test
    void sessionVariables_doNotLeakAcrossPooledTransactions() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            setTenant(c, a.tenantId(), null, "PLANT_ADMIN");
            assertThat(count(c, "select count(*) from work_orders")).isEqualTo(2);
            c.commit();
            // Same physical connection, next transaction: set_config(..., true) has expired.
            assertThat(count(c, "select count(*) from work_orders")).isZero();
            c.commit();
            c.setAutoCommit(true);
        }
    }

    interface SqlWork {
        void run(Connection c) throws SQLException;
    }

    private void inTenantTransaction(UUID tenant, UUID user, String role, SqlWork work) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                setTenant(c, tenant, user, role);
                work.run(c);
            } finally {
                c.rollback();
                c.setAutoCommit(true);
            }
        }
    }

    private static void setTenant(Connection c, UUID tenant, UUID user, String role) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "select set_config('app.tenant_id', ?, true), set_config('app.user_id', ?, true), set_config('app.role', ?, true)")) {
            ps.setString(1, tenant.toString());
            ps.setString(2, user == null ? "" : user.toString());
            ps.setString(3, role);
            ps.execute();
        }
    }

    private static long count(Connection c, String sql) throws SQLException {
        try (ResultSet rs = c.createStatement().executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
