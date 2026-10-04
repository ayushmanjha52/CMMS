package com.plantdesk.performance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantdesk.asset.AssetLevel;
import com.plantdesk.support.AbstractIntegrationTest;
import com.plantdesk.support.TestData.Plant;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Measured, not asserted by eye: Hibernate's statement counter around a real HTTP request.
 * Remove the @EntityGraph from WorkOrderRepository.findAll and the work order test measures
 * 24 statements for a 20-row page (page + count + 20 asset loads + 2 assignee loads)
 * instead of 2. That was checked by actually removing it.
 */
class QueryCountIT extends AbstractIntegrationTest {

    @Autowired
    EntityManagerFactory emf;

    @Autowired
    ObjectMapper json;

    Statistics stats;
    Plant p;

    @BeforeEach
    void setUp() {
        stats = emf.unwrap(SessionFactory.class).getStatistics();
        p = data.newPlant();
    }

    @Test
    void assetTreeOf50_loadsInOneQuery() throws Exception {
        // newPlant made PLANT + 1 AREA + 1 MACHINE. Add 3 areas, 12 lines, 32 machines = 50.
        data.run(p.tenantId(), () -> {
            List<UUID> areas = new ArrayList<>(List.of(p.areaId()));
            for (int i = 0; i < 3; i++) areas.add(data.create(p.plantAssetId(), AssetLevel.AREA, "Area " + i, null, null));
            List<UUID> lines = new ArrayList<>();
            for (UUID area : areas) for (int i = 0; i < 3; i++) lines.add(data.create(area, AssetLevel.LINE, "Line " + i, null, null));
            for (int i = 0; i < 32; i++) data.create(lines.get(i % lines.size()), AssetLevel.MACHINE, "Motor " + i, "MTR", null);
        });

        stats.clear();
        MvcResult result = mvc.perform(get("/api/assets/tree").header("Authorization", bearer(p.admin())))
                .andExpect(status().isOk()).andReturn();
        long statements = stats.getPrepareStatementCount();

        assertThat(countNodes(json.readTree(result.getResponse().getContentAsString()))).isEqualTo(50);
        assertThat(statements).as("statements to load a 50-asset tree").isEqualTo(1);
    }

    @Test
    void workOrderList_doesNotGrowWithRowCount() throws Exception {
        for (int i = 0; i < 30; i++) {
            UUID machine = data.machine(p, "Pump " + i, null);
            data.openBreakdown(p, machine, i % 2 == 0 ? p.tech1() : p.tech2());
        }

        stats.clear();
        MvcResult result = mvc.perform(get("/api/work-orders").param("size", "20").header("Authorization", bearer(p.manager())))
                .andExpect(status().isOk()).andReturn();
        long statements = stats.getPrepareStatementCount();

        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("content")).hasSize(20);
        assertThat(body.get("totalElements").asInt()).isEqualTo(30);
        // Every row renders an asset tag and an assignee name: one page SELECT with joins, one COUNT.
        assertThat(statements).as("statements for a 20-row page that shows asset + assignee").isLessThanOrEqualTo(2);
    }

    private static int countNodes(JsonNode nodes) {
        int n = 0;
        for (JsonNode node : nodes) {
            n += 1 + countNodes(node.get("children"));
        }
        return n;
    }
}
