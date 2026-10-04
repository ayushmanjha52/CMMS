package com.plantdesk.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure functions, no Spring, no database — so every number on the reliability screen can be
 * reproduced on paper from the list of breakdowns.
 *
 * <p>Definitions (calendar-time basis):
 * <ul>
 *   <li><b>Observed</b> = window end − max(window start, in service since)</li>
 *   <li><b>Downtime</b> = Σ (restored − failed), an unrestored failure counts up to window end</li>
 *   <li><b>MTBF</b> = (observed − downtime) / failures — mean <em>up</em>time between failures</li>
 *   <li><b>MTTR</b> = Σ repair time of restored failures / restored failures</li>
 * </ul>
 * Calendar time rather than running hours: hour meters are read irregularly and not every
 * asset has one, while the failure and restoration timestamps are always on the job card.
 */
public final class ReliabilityCalculator {

    private static final BigDecimal SIXTY = BigDecimal.valueOf(60);

    private ReliabilityCalculator() {}

    public record Failure(Instant failedAt, Instant restoredAt) {}

    public record Result(int failures, int restoredFailures, long observedMinutes, long downtimeMinutes,
                         BigDecimal mtbfHours, BigDecimal mttrHours, BigDecimal availabilityPercent) {}

    public static Result compute(List<Failure> failures, Instant observedFrom, Instant to) {
        long observed = Math.max(0, Duration.between(observedFrom, to).toMinutes());
        long downtime = 0;
        long repairMinutes = 0;
        int restored = 0;
        for (Failure f : failures) {
            Instant end = f.restoredAt() == null || f.restoredAt().isAfter(to) ? to : f.restoredAt();
            downtime += Math.max(0, Duration.between(f.failedAt(), end).toMinutes());
            if (f.restoredAt() != null) {
                restored++;
                repairMinutes += Duration.between(f.failedAt(), f.restoredAt()).toMinutes();
            }
        }
        downtime = Math.min(downtime, observed);
        long uptime = observed - downtime;
        BigDecimal mtbf = failures.isEmpty() ? null
                : BigDecimal.valueOf(uptime).divide(BigDecimal.valueOf(failures.size()).multiply(SIXTY), 1, RoundingMode.HALF_UP);
        BigDecimal mttr = restored == 0 ? null
                : BigDecimal.valueOf(repairMinutes).divide(BigDecimal.valueOf(restored).multiply(SIXTY), 1, RoundingMode.HALF_UP);
        BigDecimal availability = observed == 0 ? null
                : BigDecimal.valueOf(uptime * 100).divide(BigDecimal.valueOf(observed), 2, RoundingMode.HALF_UP);
        return new Result(failures.size(), restored, observed, downtime, mtbf, mttr, availability);
    }

    public enum Trend { DEGRADING, STABLE, IMPROVING, INSUFFICIENT_DATA }

    /** Rolling MTBF at each failure: mean of the last (up to) three gaps between failures. */
    public record TrendPoint(Instant at, BigDecimal rollingMtbfHours) {}

    public record TrendResult(Trend trend, BigDecimal olderMeanGapHours, BigDecimal recentMeanGapHours,
                              List<TrendPoint> points) {}

    static final int ROLLING_WINDOW = 3;
    static final int MIN_GAPS = 3;
    static final BigDecimal DEGRADING_RATIO = new BigDecimal("0.75");
    static final BigDecimal IMPROVING_RATIO = new BigDecimal("1.25");

    /**
     * Is the gap between failures shrinking? Split the gaps into an older half and a recent
     * half (the middle gap is dropped when the count is odd) and compare their means. Recent
     * below 75% of older = degrading — the ticks are bunching up toward the right of the strip.
     */
    public static TrendResult trend(List<Instant> failureTimes) {
        List<Long> gaps = new ArrayList<>();
        List<TrendPoint> points = new ArrayList<>();
        for (int i = 1; i < failureTimes.size(); i++) {
            gaps.add(Duration.between(failureTimes.get(i - 1), failureTimes.get(i)).toMinutes());
            int from = Math.max(0, gaps.size() - ROLLING_WINDOW);
            points.add(new TrendPoint(failureTimes.get(i), hours(mean(gaps.subList(from, gaps.size())))));
        }
        if (gaps.size() < MIN_GAPS) {
            return new TrendResult(Trend.INSUFFICIENT_DATA, null, null, points);
        }
        int half = gaps.size() / 2;
        BigDecimal older = mean(gaps.subList(0, half));
        BigDecimal recent = mean(gaps.subList(gaps.size() - half, gaps.size()));
        Trend trend;
        if (recent.compareTo(older.multiply(DEGRADING_RATIO)) < 0) {
            trend = Trend.DEGRADING;
        } else if (recent.compareTo(older.multiply(IMPROVING_RATIO)) > 0) {
            trend = Trend.IMPROVING;
        } else {
            trend = Trend.STABLE;
        }
        return new TrendResult(trend, hours(older), hours(recent), points);
    }

    private static BigDecimal mean(List<Long> values) {
        long sum = values.stream().mapToLong(Long::longValue).sum();
        return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal hours(BigDecimal minutes) {
        return minutes.divide(SIXTY, 1, RoundingMode.HALF_UP);
    }
}
