package com.plantdesk.analytics;

import com.plantdesk.analytics.ReliabilityCalculator.Failure;
import com.plantdesk.analytics.ReliabilityCalculator.Trend;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The hand-worked example (also used end-to-end in ReliabilityIT):
 *
 * <pre>
 * Observed  1 Mar → 30 Jun 2026 = 121 days            = 174 240 min
 * Failures  11 Mar (2 h), 20 Apr (4 h), 20 May (6 h), 9 Jun (4 h), 24 Jun (8 h)
 * Downtime  2+4+6+4+8 = 24 h                          =   1 440 min
 * Uptime    174 240 − 1 440                           = 172 800 min
 * MTBF      172 800 / 5 / 60                          = 576.0 h
 * MTTR      24 / 5                                    =   4.8 h
 * Avail.    172 800 / 174 240                         =  99.17 %
 * Gaps      40, 30, 20, 15 days → older mean 35 d = 840 h, recent 17.5 d = 420 h
 *           420 < 0.75 × 840 = 630 → DEGRADING
 * Rolling   960, 840, 720, 520 h  (means of last ≤3 gaps: 40 | 40,30 | 40,30,20 | 30,20,15 days)
 * </pre>
 */
class ReliabilityCalculatorTest {

    static final Instant FROM = Instant.parse("2026-03-01T00:00:00Z");
    static final Instant TO = Instant.parse("2026-06-30T00:00:00Z");

    static final List<Instant> FAILED_AT = List.of(
            Instant.parse("2026-03-11T00:00:00Z"), Instant.parse("2026-04-20T00:00:00Z"),
            Instant.parse("2026-05-20T00:00:00Z"), Instant.parse("2026-06-09T00:00:00Z"),
            Instant.parse("2026-06-24T00:00:00Z"));
    static final int[] DOWNTIME_HOURS = {2, 4, 6, 4, 8};

    @Test
    void mtbfMttrAndAvailability_matchTheWorkedExample() {
        List<Failure> failures = new java.util.ArrayList<>();
        for (int i = 0; i < FAILED_AT.size(); i++) {
            failures.add(new Failure(FAILED_AT.get(i), FAILED_AT.get(i).plus(Duration.ofHours(DOWNTIME_HOURS[i]))));
        }
        var r = ReliabilityCalculator.compute(failures, FROM, TO);

        assertThat(r.failures()).isEqualTo(5);
        assertThat(r.observedMinutes()).isEqualTo(174_240);
        assertThat(r.downtimeMinutes()).isEqualTo(1_440);
        assertThat(r.mtbfHours()).isEqualByComparingTo("576.0");
        assertThat(r.mttrHours()).isEqualByComparingTo("4.8");
        assertThat(r.availabilityPercent()).isEqualByComparingTo("99.17");
    }

    @Test
    void shrinkingGaps_areDegrading_withTheExpectedRollingLine() {
        var t = ReliabilityCalculator.trend(FAILED_AT);
        assertThat(t.trend()).isEqualTo(Trend.DEGRADING);
        assertThat(t.olderMeanGapHours()).isEqualByComparingTo("840.0");
        assertThat(t.recentMeanGapHours()).isEqualByComparingTo("420.0");
        assertThat(t.points()).extracting(ReliabilityCalculator.TrendPoint::rollingMtbfHours)
                .usingElementComparator(java.math.BigDecimal::compareTo)
                .containsExactly(new java.math.BigDecimal("960.0"), new java.math.BigDecimal("840.0"),
                        new java.math.BigDecimal("720.0"), new java.math.BigDecimal("520.0"));
    }

    @Test
    void steadyGaps_areStable_andTooFewFailuresSayNothing() {
        Instant t = FROM;
        var steady = List.of(t, t.plus(Duration.ofDays(30)), t.plus(Duration.ofDays(60)), t.plus(Duration.ofDays(90)));
        assertThat(ReliabilityCalculator.trend(steady).trend()).isEqualTo(Trend.STABLE);
        assertThat(ReliabilityCalculator.trend(steady.subList(0, 3)).trend()).isEqualTo(Trend.INSUFFICIENT_DATA);
    }

    @Test
    void noFailures_meansNoMtbf_notInfinity() {
        var r = ReliabilityCalculator.compute(List.of(), FROM, TO);
        assertThat(r.mtbfHours()).isNull();
        assertThat(r.mttrHours()).isNull();
        assertThat(r.availabilityPercent()).isEqualByComparingTo("100.00");
    }

    @Test
    void anUnrestoredFailure_countsDowntimeToWindowEnd_butNotTowardMttr() {
        var r = ReliabilityCalculator.compute(List.of(
                new Failure(TO.minus(Duration.ofHours(10)), null),
                new Failure(FROM, FROM.plus(Duration.ofHours(2)))), FROM, TO);
        assertThat(r.downtimeMinutes()).isEqualTo(12 * 60);
        assertThat(r.restoredFailures()).isEqualTo(1);
        assertThat(r.mttrHours()).isEqualByComparingTo("2.0");
    }
}
