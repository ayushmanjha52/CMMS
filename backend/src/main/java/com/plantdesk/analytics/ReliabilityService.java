package com.plantdesk.analytics;

import com.plantdesk.analytics.ReliabilityCalculator.Failure;
import com.plantdesk.analytics.ReliabilityCalculator.Result;
import com.plantdesk.analytics.ReliabilityCalculator.Trend;
import com.plantdesk.analytics.ReliabilityCalculator.TrendResult;
import com.plantdesk.asset.Asset;
import com.plantdesk.asset.AssetRepository;
import com.plantdesk.asset.Criticality;
import com.plantdesk.common.NotFoundException;
import com.plantdesk.part.SparePart;
import com.plantdesk.part.SparePartRepository;
import com.plantdesk.workorder.Priority;
import com.plantdesk.workorder.WorkOrder;
import com.plantdesk.workorder.WorkOrderRepository;
import com.plantdesk.workorder.WorkOrderService;
import com.plantdesk.workorder.WorkOrderStatus;
import com.plantdesk.workorder.WorkOrderType;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ReliabilityService {

    private final WorkOrderRepository workOrders;
    private final AssetRepository assets;
    private final SparePartRepository parts;
    private final Clock clock;

    public ReliabilityService(WorkOrderRepository workOrders, AssetRepository assets, SparePartRepository parts, Clock clock) {
        this.workOrders = workOrders;
        this.assets = assets;
        this.parts = parts;
        this.clock = clock;
    }

    public record AssetReliability(UUID assetId, String tag, String name, Criticality criticality,
                                   Instant from, Instant to, Result result) {}

    public record StripFailure(UUID workOrderId, String number, Instant at, Priority priority, String failureCode,
                               String failureDescription, Long downtimeMinutes) {}

    public record HealthStrip(UUID assetId, String tag, String name, Instant from, Instant to,
                              List<StripFailure> failures, TrendResult trend, Result summary) {}

    public record DegradingAsset(UUID assetId, String tag, String name, Criticality criticality, int failures,
                                 BigDecimal olderMeanGapHours, BigDecimal recentMeanGapHours) implements Serializable {}

    public record DowntimeBucket(String key, String label, long downtimeMinutes, int breakdowns) {}

    public record DowntimeReport(Instant from, Instant to, List<DowntimeBucket> byArea, List<DowntimeBucket> topAssets) {}

    public record Summary(Map<Priority, Long> openByPriority, long openBreakdowns, long overdue,
                          long breakdownsLast30Days, long lowStockParts) {}

    public AssetReliability reliability(UUID assetId, int months) {
        Asset asset = loadAsset(assetId);
        Instant to = clock.instant();
        Instant from = monthsBefore(to, months);
        Instant observedFrom = later(from, asset.inServiceSince());
        List<WorkOrder> breakdowns = countedFailures(workOrders.findBreakdownsForAssetBetween(assetId, from, to));
        return new AssetReliability(asset.getId(), asset.getTag(), asset.getName(), asset.getCriticality(),
                observedFrom, to, ReliabilityCalculator.compute(toFailures(breakdowns), observedFrom, to));
    }

    /** Data for the 12-month health strip: one tick per failure, rolling MTBF line, trend verdict. */
    public HealthStrip healthStrip(UUID assetId) {
        Asset asset = loadAsset(assetId);
        Instant to = clock.instant();
        Instant from = monthsBefore(to, 12);
        List<WorkOrder> breakdowns = countedFailures(workOrders.findBreakdownsForAssetBetween(assetId, from, to));
        List<StripFailure> ticks = breakdowns.stream().map(w -> new StripFailure(w.getId(), w.getNumber(),
                w.getDowntimeStart(), w.getPriority(),
                w.getFailureCode() == null ? null : w.getFailureCode().name(),
                w.getFailureCode() == null ? null : w.getFailureCode().description(),
                w.getDowntimeEnd() == null ? null : Duration.between(w.getDowntimeStart(), w.getDowntimeEnd()).toMinutes()))
                .toList();
        TrendResult trend = ReliabilityCalculator.trend(breakdowns.stream().map(WorkOrder::getDowntimeStart).toList());
        Result summary = ReliabilityCalculator.compute(toFailures(breakdowns), later(from, asset.inServiceSince()), to);
        return new HealthStrip(asset.getId(), asset.getTag(), asset.getName(), from, to, ticks, trend, summary);
    }

    /**
     * Cached per tenant. The tenant id is part of the key — a cache keyed only by method
     * arguments (none here) would serve plant A's degrading list to plant B.
     */
    @Cacheable(cacheNames = WorkOrderService.ANALYTICS_CACHE,
            key = "T(com.plantdesk.tenancy.TenantContext).require().tenantId()")
    public List<DegradingAsset> degrading() {
        Instant to = clock.instant();
        Map<Asset, List<WorkOrder>> byAsset = countedFailures(workOrders.findBreakdownsBetween(monthsBefore(to, 12), to))
                .stream().collect(Collectors.groupingBy(WorkOrder::getAsset, LinkedHashMap::new, Collectors.toList()));
        return byAsset.entrySet().stream()
                .map(e -> {
                    TrendResult t = ReliabilityCalculator.trend(e.getValue().stream().map(WorkOrder::getDowntimeStart).toList());
                    return t.trend() != Trend.DEGRADING ? null : new DegradingAsset(e.getKey().getId(), e.getKey().getTag(),
                            e.getKey().getName(), e.getKey().getCriticality(), e.getValue().size(),
                            t.olderMeanGapHours(), t.recentMeanGapHours());
                })
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(DegradingAsset::criticality)
                        .thenComparing(d -> d.recentMeanGapHours().divide(d.olderMeanGapHours(), 4, java.math.RoundingMode.HALF_UP)))
                .toList();
    }

    /** Breakdown downtime by area (the second tag segment) and the ten worst assets. */
    public DowntimeReport downtime(int months) {
        Instant to = clock.instant();
        Instant from = monthsBefore(to, months);
        List<WorkOrder> breakdowns = workOrders.findBreakdownsBetween(from, to);
        Map<String, long[]> areas = new LinkedHashMap<>();
        Map<Asset, long[]> perAsset = new LinkedHashMap<>();
        for (WorkOrder w : breakdowns) {
            long minutes = Duration.between(w.getDowntimeStart(), w.getDowntimeEnd() == null ? to : w.getDowntimeEnd()).toMinutes();
            String[] seg = w.getAsset().getTag().split("/");
            String area = seg.length >= 2 ? seg[0] + "/" + seg[1] : seg[0];
            accumulate(areas.computeIfAbsent(area, k -> new long[2]), minutes);
            accumulate(perAsset.computeIfAbsent(w.getAsset(), k -> new long[2]), minutes);
        }
        Map<String, String> areaNames = assets.findByTagIn(areas.keySet()).stream()
                .collect(Collectors.toMap(Asset::getTag, Asset::getName));
        List<DowntimeBucket> byArea = areas.entrySet().stream()
                .map(e -> new DowntimeBucket(e.getKey(), areaNames.getOrDefault(e.getKey(), e.getKey()), e.getValue()[0], (int) e.getValue()[1]))
                .sorted(Comparator.comparingLong(DowntimeBucket::downtimeMinutes).reversed()).toList();
        List<DowntimeBucket> top = perAsset.entrySet().stream()
                .map(e -> new DowntimeBucket(e.getKey().getTag(), e.getKey().getName(), e.getValue()[0], (int) e.getValue()[1]))
                .sorted(Comparator.comparingLong(DowntimeBucket::downtimeMinutes).reversed()).limit(10).toList();
        return new DowntimeReport(from, to, byArea, top);
    }

    public Summary summary() {
        Instant now = clock.instant();
        List<WorkOrder> outstanding = workOrders.findWithStatusIn(
                EnumSet.of(WorkOrderStatus.OPEN, WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.ON_HOLD));
        Map<Priority, Long> byPriority = new EnumMap<>(Priority.class);
        for (Priority p : Priority.values()) {
            byPriority.put(p, 0L);
        }
        outstanding.forEach(w -> byPriority.merge(w.getPriority(), 1L, Long::sum));
        long openBreakdowns = outstanding.stream().filter(w -> w.getType() == WorkOrderType.BREAKDOWN).count();
        long overdue = outstanding.stream().filter(w -> w.isOverdue(now)).count();
        long recent = workOrders.findBreakdownsBetween(now.minus(Duration.ofDays(30)), now).size();
        long lowStock = parts.findAllByOrderByPartNumberAsc().stream().filter(SparePart::isBelowReorderPoint).count();
        return new Summary(byPriority, openBreakdowns, overdue, recent, lowStock);
    }

    private Asset loadAsset(UUID id) {
        return assets.findById(id).orElseThrow(() -> new NotFoundException("Asset", id));
    }

    /** External supply interruptions (X01) are not the asset's failure. Uncoded open breakdowns still count. */
    private static List<WorkOrder> countedFailures(List<WorkOrder> breakdowns) {
        return breakdowns.stream()
                .filter(w -> w.getFailureCode() == null || w.getFailureCode().countsAsFailure())
                .toList();
    }

    private static List<Failure> toFailures(List<WorkOrder> breakdowns) {
        return breakdowns.stream().map(w -> new Failure(w.getDowntimeStart(), w.getDowntimeEnd())).toList();
    }

    private static Instant monthsBefore(Instant to, int months) {
        return to.atZone(ZoneOffset.UTC).minusMonths(Math.max(1, Math.min(months, 36))).toInstant();
    }

    private static Instant later(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private static void accumulate(long[] bucket, long minutes) {
        bucket[0] += minutes;
        bucket[1] += 1;
    }
}
