package com.plantdesk.workorder;

import com.plantdesk.part.SparePartRepository;
import com.plantdesk.support.AbstractIntegrationTest;
import com.plantdesk.support.TestData.Plant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkOrderApiIT extends AbstractIntegrationTest {

    @Autowired
    SparePartRepository parts;

    Plant p;
    UUID wo;

    @BeforeEach
    void setUp() {
        p = data.newPlant();
        wo = data.openBreakdown(p, p.machineId(), p.tech1());
    }

    @Test
    void invalidTransition_returns409_withAUsefulMessage() throws Exception {
        transition(p.manager(), wo, "{\"action\":\"CLOSE\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("Cannot CLOSE work order WO-")))
                .andExpect(jsonPath("$.detail", containsString("it is OPEN")))
                .andExpect(jsonPath("$.detail", containsString("only allowed from [COMPLETED]")));
    }

    @Test
    void returnToServiceWithoutLabour_returns409() throws Exception {
        transition(p.tech1(), wo, "{\"action\":\"START\"}").andExpect(status().isOk());
        transition(p.tech1(), wo, "{\"action\":\"RETURN_TO_SERVICE\",\"failureCode\":\"M01\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("Record labour hours")));
    }

    @Test
    void unknownStatusOrAction_neverReachesTheDatabase() throws Exception {
        transition(p.manager(), wo, "{\"action\":\"CLOSED\"}").andExpect(status().isBadRequest());
        transition(p.manager(), wo, "{\"action\":\"DELETE_EVERYTHING\"}").andExpect(status().isBadRequest());
    }

    @Test
    void fullLifecycle_withLabourAndParts_costsExactly() throws Exception {
        UUID bearing = data.part(p, "BRG-6319", "5", "18450.00");

        transition(p.tech1(), wo, "{\"action\":\"START\"}").andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        json(p.tech1(), "/api/work-orders/{id}/labour", wo,
                "{\"minutes\":95,\"workDate\":\"" + LocalDate.now() + "\",\"note\":\"Replaced DE bearing\"}")
                .andExpect(status().isOk());
        json(p.tech1(), "/api/work-orders/{id}/parts", wo, "{\"sparePartId\":\"" + bearing + "\",\"quantity\":2}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partsCostTotal").value(36900.00))
                .andExpect(jsonPath("$.labourMinutesTotal").value(95));

        transition(p.tech1(), wo, "{\"action\":\"RETURN_TO_SERVICE\",\"failureCode\":\"M01\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.downtimeEnd").isNotEmpty())
                .andExpect(jsonPath("$.availableActions").isEmpty()); // technician cannot close
        transition(p.manager(), wo, "{\"action\":\"CLOSE\",\"note\":\"OK\"}")
                .andExpect(jsonPath("$.status").value("CLOSED"));

        BigDecimal stock = data.as(p.tenantId(), () -> parts.findById(bearing).orElseThrow().getStockQty());
        assertThat(stock).isEqualByComparingTo("3");
    }

    @Test
    void insufficientStock_returns409_andLeavesStockUntouched() throws Exception {
        UUID seal = data.part(p, "SEAL-1", "1", "27800.00");
        transition(p.tech1(), wo, "{\"action\":\"START\"}");
        json(p.tech1(), "/api/work-orders/{id}/parts", wo, "{\"sparePartId\":\"" + seal + "\",\"quantity\":2}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("Only 1 EA of SEAL-1 in stock")));
        BigDecimal stock = data.as(p.tenantId(), () -> parts.findById(seal).orElseThrow().getStockQty());
        assertThat(stock).isEqualByComparingTo("1");
    }

    @Test
    void technicianCannotApproveClosure() throws Exception {
        UUID done = data.closedBreakdown(p, p.machineId(), java.time.Instant.now().minusSeconds(7200),
                java.time.Duration.ofHours(1), FailureCode.E02);
        transition(p.tech1(), done, "{\"action\":\"REWORK\",\"note\":\"x\"}").andExpect(status().isForbidden());
    }

    @Test
    void technicianRaisesBreakdownsOnly_andIsAssignedToThem() throws Exception {
        String raise = "{\"assetId\":\"" + p.machineId() + "\",\"type\":\"%s\",\"priority\":\"HIGH\",\"title\":\"Trip\"}";
        mvc.perform(post("/api/work-orders").header("Authorization", bearer(p.tech2()))
                        .contentType(MediaType.APPLICATION_JSON).content(raise.formatted("PREVENTIVE")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/work-orders").header("Authorization", bearer(p.tech2()))
                        .contentType(MediaType.APPLICATION_JSON).content(raise.formatted("BREAKDOWN")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assignee.id").value(p.tech2().getId().toString()))
                .andExpect(jsonPath("$.number", containsString("WO-")));
    }

    @Test
    void viewerIsReadOnly() throws Exception {
        mvc.perform(post("/api/work-orders").header("Authorization", bearer(p.viewer()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetId\":\"" + p.machineId() + "\",\"type\":\"BREAKDOWN\",\"priority\":\"HIGH\",\"title\":\"x\"}"))
                .andExpect(status().isForbidden());
        transition(p.viewer(), wo, "{\"action\":\"START\"}").andExpect(status().isForbidden());
    }

    private ResultActions transition(com.plantdesk.user.User who, UUID id, String body) throws Exception {
        return json(who, "/api/work-orders/{id}/transitions", id, body);
    }

    private ResultActions json(com.plantdesk.user.User who, String url, UUID id, String body) throws Exception {
        return mvc.perform(post(url, id).header("Authorization", bearer(who))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
