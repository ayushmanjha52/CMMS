package com.plantdesk.isolation;

import com.plantdesk.support.AbstractIntegrationTest;
import com.plantdesk.support.DbTarget;
import com.plantdesk.support.TestData.Plant;
import com.plantdesk.workorder.WorkOrder;
import com.plantdesk.workorder.WorkOrderRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * First line of defence, tested alone. RLS is switched OFF on the relevant tables for the
 * duration of each test (via the owner connection), so only the Hibernate filter remains.
 */
class HibernateTenantFilterIT extends AbstractIntegrationTest {

    private static final String[] TABLES = {"work_orders", "assets", "users"};

    @Autowired
    WorkOrderRepository workOrders;

    @Autowired
    EntityManager em;

    Plant a;
    Plant b;
    UUID bWorkOrder;

    @BeforeEach
    void setUp() throws SQLException {
        a = data.newPlant();
        b = data.newPlant();
        data.openBreakdown(a, a.machineId(), null);
        bWorkOrder = data.openBreakdown(b, b.machineId(), null);
        setRls(false);
    }

    @AfterEach
    void restoreRls() throws SQLException {
        setRls(true);
    }

    @Test
    void repositoryFindById_isFiltered_evenWithRlsOff() {
        boolean found = data.as(a.tenantId(), () -> workOrders.findById(bWorkOrder).isPresent());
        assertThat(found).isFalse();
    }

    @Test
    void repositoryQueries_areFiltered_evenWithRlsOff() {
        var ids = data.as(a.tenantId(), () -> workOrders.findAll(
                (root, q, cb) -> cb.conjunction(), Pageable.unpaged()).map(WorkOrder::getId).toList());
        assertThat(ids).isNotEmpty().doesNotContain(bWorkOrder);
    }

    /**
     * Documents WHY TenantAwareJpaRepository exists. EntityManager.find loads by primary key
     * and Hibernate does not apply filters to it — with RLS off, it returns plant B's row.
     * If this assertion ever starts failing, Hibernate has changed behaviour; re-evaluate.
     */
    @Test
    void rawEntityManagerFind_bypassesTheFilter_whichIsWhyFindByIdIsOverridden() {
        WorkOrder leaked = data.as(a.tenantId(), () -> em.find(WorkOrder.class, bWorkOrder));
        assertThat(leaked).as("em.find skips Hibernate filters").isNotNull();
    }

    private static void setRls(boolean enabled) throws SQLException {
        DbTarget db = DbTarget.get();
        try (Connection c = DriverManager.getConnection(db.jdbcUrl(), db.ownerUser(), db.ownerPassword())) {
            for (String t : TABLES) {
                c.createStatement().execute("alter table " + t + (enabled ? " enable" : " disable") + " row level security");
            }
        }
    }
}
