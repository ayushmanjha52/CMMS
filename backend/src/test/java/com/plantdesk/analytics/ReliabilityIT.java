package com.plantdesk.analytics;

import com.plantdesk.support.AbstractIntegrationTest;
import com.plantdesk.support.TestData.Plant;
import com.plantdesk.workorder.FailureCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The worked example from ReliabilityCalculatorTest, end to end: real work orders through
 * the real lifecycle, read back over HTTP. Plus one supply-interruption breakdown (X01)
 * that must NOT count.
 */
class ReliabilityIT extends AbstractIntegrationTest {

    Plant p;
    UUID motor;

    @BeforeEach
    void seed() {
        clock.set(Instant.parse("2026-06-30T00:00:00Z"));
        p = data.newPlant();
        motor = data.machine(p, "Conveyor drive motor", LocalDate.of(2026, 3, 1));
        for (int i = 0; i < ReliabilityCalculatorTest.FAILED_AT.size(); i++) {
            data.closedBreakdown(p, motor, ReliabilityCalculatorTest.FAILED_AT.get(i),
                    Duration.ofHours(ReliabilityCalculatorTest.DOWNTIME_HOURS[i]), FailureCode.M01);
        }
        data.closedBreakdown(p, motor, Instant.parse("2026-06-15T00:00:00Z"), Duration.ofHours(1), FailureCode.X01);
    }

    @Test
    void reliabilityEndpoint_matchesTheHandCalculation() throws Exception {
        mvc.perform(get("/api/analytics/assets/{id}/reliability", motor).header("Authorization", bearer(p.manager())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.failures").value(5))
                .andExpect(jsonPath("$.result.observedMinutes").value(174240))
                .andExpect(jsonPath("$.result.downtimeMinutes").value(1440))
                .andExpect(jsonPath("$.result.mtbfHours").value(576.0))
                .andExpect(jsonPath("$.result.mttrHours").value(4.8))
                .andExpect(jsonPath("$.result.availabilityPercent").value(99.17));
    }

    @Test
    void healthStrip_showsFiveTicks_andFlagsTheTrend() throws Exception {
        mvc.perform(get("/api/analytics/assets/{id}/health-strip", motor).header("Authorization", bearer(p.viewer())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failures.length()").value(5))
                .andExpect(jsonPath("$.trend.trend").value("DEGRADING"))
                .andExpect(jsonPath("$.trend.olderMeanGapHours").value(840.0))
                .andExpect(jsonPath("$.trend.recentMeanGapHours").value(420.0))
                .andExpect(jsonPath("$.trend.points[3].rollingMtbfHours").value(520.0));
    }

    @Test
    void degradingAssets_listIncludesTheMotor() throws Exception {
        mvc.perform(get("/api/analytics/degrading").header("Authorization", bearer(p.admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].assetId", hasItem(motor.toString())));
    }

    @Test
    void techniciansDoNotGetPlantWideAnalytics() throws Exception {
        mvc.perform(get("/api/analytics/summary").header("Authorization", bearer(p.tech1())))
                .andExpect(status().isForbidden());
    }
}
