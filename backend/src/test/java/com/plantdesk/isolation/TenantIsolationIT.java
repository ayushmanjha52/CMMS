package com.plantdesk.isolation;

import com.plantdesk.support.AbstractIntegrationTest;
import com.plantdesk.support.TestData.Plant;
import com.plantdesk.workorder.WorkOrderRepository;
import com.plantdesk.workorder.WorkOrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * THE most important test in the repository.
 *
 * <p>Authenticated as plant A, every way of reaching plant B's data must come back empty or
 * 404 — through the full stack (JWT → TenantContext → Hibernate filter → RLS). The layer
 * tests (HibernateTenantFilterIT, RowLevelSecurityIT) prove each defence also holds alone.
 */
class TenantIsolationIT extends AbstractIntegrationTest {

    @Autowired
    WorkOrderRepository workOrders;

    Plant a;
    Plant b;
    UUID aWorkOrder;
    UUID bWorkOrder;

    @BeforeEach
    void twoPlants() {
        a = data.newPlant();
        b = data.newPlant();
        aWorkOrder = data.openBreakdown(a, a.machineId(), a.tech1());
        bWorkOrder = data.openBreakdown(b, b.machineId(), b.tech1());
    }

    @Test
    void tenantB_workOrder_isInvisible_whenQueriedByDirectId() throws Exception {
        mvc.perform(get("/api/work-orders/{id}", bWorkOrder).header("Authorization", bearer(a.manager())))
                .andExpect(status().isNotFound());
        // Sanity: the same request as B's own manager succeeds, so the 404 above is isolation, not a broken endpoint.
        mvc.perform(get("/api/work-orders/{id}", bWorkOrder).header("Authorization", bearer(b.manager())))
                .andExpect(status().isOk());
    }

    @Test
    void tenantB_workOrders_areAbsentFromTheList() throws Exception {
        mvc.perform(get("/api/work-orders").param("size", "200").header("Authorization", bearer(a.manager())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem(aWorkOrder.toString())))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(bWorkOrder.toString()))))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void tenantIdRequestParameter_isIgnored() throws Exception {
        mvc.perform(get("/api/work-orders").param("tenantId", b.tenantId().toString())
                        .header("Authorization", bearer(a.manager())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", not(hasItem(bWorkOrder.toString()))));
        mvc.perform(get("/api/work-orders/{id}", bWorkOrder).param("tenantId", b.tenantId().toString())
                        .header("X-Tenant-Id", b.tenantId().toString())
                        .header("Authorization", bearer(a.manager())))
                .andExpect(status().isNotFound());
    }

    @Test
    void tenantB_workOrder_cannotBeTransitioned_orAssigned() throws Exception {
        mvc.perform(post("/api/work-orders/{id}/transitions", bWorkOrder)
                        .header("Authorization", bearer(a.manager()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"CANCEL\",\"note\":\"cross-tenant attempt\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/work-orders/{id}/assign", bWorkOrder)
                        .header("Authorization", bearer(a.manager()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"technicianId\":\"" + a.tech2().getId() + "\"}"))
                .andExpect(status().isNotFound());

        WorkOrderStatus statusInB = data.as(b.tenantId(), () -> workOrders.findById(bWorkOrder).orElseThrow().getStatus());
        assertThat(statusInB).isEqualTo(WorkOrderStatus.OPEN);
    }

    @Test
    void tenantB_technician_cannotBeAssigned_toTenantA_workOrder() throws Exception {
        mvc.perform(post("/api/work-orders/{id}/assign", aWorkOrder)
                        .header("Authorization", bearer(a.manager()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"technicianId\":\"" + b.tech1().getId() + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void tenantB_asset_isInvisible_andCannotBeUsedToRaiseWork() throws Exception {
        mvc.perform(get("/api/assets/{id}", b.machineId()).header("Authorization", bearer(a.admin())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/assets/tree").header("Authorization", bearer(a.admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(b.plantAssetId().toString()))))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(post("/api/work-orders")
                        .header("Authorization", bearer(a.manager()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetId\":\"" + b.machineId() + "\",\"type\":\"BREAKDOWN\",\"priority\":\"HIGH\",\"title\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void tenantB_analytics_areInvisible() throws Exception {
        mvc.perform(get("/api/analytics/assets/{id}/health-strip", b.machineId()).header("Authorization", bearer(a.manager())))
                .andExpect(status().isNotFound());
    }

    @Test
    void technician_seesOnlyTheirOwnWorkOrders_onTheSharedListEndpoint() throws Exception {
        UUID tech2Order = data.openBreakdown(a, a.machineId(), a.tech2());
        UUID unassigned = data.openBreakdown(a, a.machineId(), null);

        mvc.perform(get("/api/work-orders").header("Authorization", bearer(a.tech1())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem(aWorkOrder.toString())))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(tech2Order.toString()))))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(unassigned.toString()))))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/work-orders/{id}", tech2Order).header("Authorization", bearer(a.tech1())))
                .andExpect(status().isNotFound());
        // The manager on the same endpoint sees all three.
        mvc.perform(get("/api/work-orders").header("Authorization", bearer(a.manager())))
                .andExpect(jsonPath("$.totalElements").value(3));
    }
}
