package com.plantdesk.pm;

import com.plantdesk.asset.Asset;
import com.plantdesk.asset.AssetDtos;
import com.plantdesk.asset.AssetRepository;
import com.plantdesk.common.NotFoundException;
import com.plantdesk.user.Trade;
import com.plantdesk.workorder.Priority;
import com.plantdesk.workorder.WorkOrder;
import com.plantdesk.workorder.WorkOrderRepository;
import com.plantdesk.workorder.WorkOrderReturnedToService;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PmScheduleService {

    private final PmScheduleRepository schedules;
    private final AssetRepository assets;
    private final WorkOrderRepository workOrders;
    private final Clock clock;

    public PmScheduleService(PmScheduleRepository schedules, AssetRepository assets,
                             WorkOrderRepository workOrders, Clock clock) {
        this.schedules = schedules;
        this.assets = assets;
        this.workOrders = workOrders;
        this.clock = clock;
    }

    public enum State { INACTIVE, OVERDUE, SCHEDULED, DUE, APPROACHING, OK }

    public record OpenOrder(UUID id, String number, boolean overdue) {}

    public record ScheduleView(UUID id, UUID assetId, String assetTag, String assetName, String title, Trade trade,
                               Priority priority, Integer intervalDays, BigDecimal intervalRunningHours,
                               Instant lastDoneAt, BigDecimal lastDoneRunningHours,
                               Instant nextDueAt, BigDecimal nextDueRunningHours, BigDecimal runningHoursRemaining,
                               State state, OpenOrder openOrder, boolean active) {}

    public record CreateRequest(@NotNull UUID assetId,
                                @NotBlank @Size(max = 200) String title,
                                @Size(max = 4000) String instructions,
                                @NotNull Trade trade,
                                @NotNull Priority priority,
                                @Min(1) @Max(3650) Integer intervalDays,
                                @DecimalMin("0.1") @Digits(integer = 6, fraction = 1) BigDecimal intervalRunningHours,
                                Instant lastDoneAt,
                                @DecimalMin("0.0") @Digits(integer = 7, fraction = 1) BigDecimal lastDoneRunningHours) {}

    @Transactional(readOnly = true)
    public List<ScheduleView> list() {
        Instant now = clock.instant();
        Map<UUID, WorkOrder> openBySchedule = workOrders.findPmOrdersWithStatusIn(PmGenerationService.OUTSTANDING)
                .stream().collect(Collectors.toMap(WorkOrder::getPmScheduleId, Function.identity(), (a, b) -> a));
        return schedules.findAllWithAsset().stream().map(s -> view(s, openBySchedule.get(s.getId()), now)).toList();
    }

    @Transactional
    public ScheduleView create(CreateRequest req) {
        Asset asset = assets.findById(req.assetId()).orElseThrow(() -> new NotFoundException("Asset", req.assetId()));
        Instant now = clock.instant();
        Long intervalMinutes = toMinutes(req.intervalRunningHours());
        Long baselineMinutes = toMinutes(req.lastDoneRunningHours());
        PmSchedule s = new PmSchedule(asset, req.title(), req.instructions(), req.trade(), req.priority(),
                req.intervalDays(), intervalMinutes,
                req.lastDoneAt() == null ? now : req.lastDoneAt(),
                baselineMinutes == null ? asset.getRunningMinutes() : baselineMinutes, now);
        schedules.save(s);
        return view(s, null, now);
    }

    @Transactional
    public ScheduleView setActive(UUID id, boolean active) {
        PmSchedule s = schedules.findById(id).orElseThrow(() -> new NotFoundException("PM schedule", id));
        s.setActive(active);
        return view(s, null, clock.instant());
    }

    /** Runs inside the work order transition's transaction: both commit or neither does. */
    @EventListener
    public void onReturnedToService(WorkOrderReturnedToService event) {
        schedules.findById(event.pmScheduleId())
                .ifPresent(s -> s.markDone(event.completedAt(), event.runningMinutesAtCompletion()));
    }

    private ScheduleView view(PmSchedule s, WorkOrder open, Instant now) {
        Asset asset = s.getAsset();
        PmSchedule.Evaluation e = s.evaluate(now, asset.getRunningMinutes());
        State state;
        if (!s.isActive()) {
            state = State.INACTIVE;
        } else if (open != null) {
            state = open.isOverdue(now) ? State.OVERDUE : State.SCHEDULED;
        } else if (e.shouldGenerate()) {
            state = State.DUE;
        } else if (approaching(s, e, now)) {
            state = State.APPROACHING;
        } else {
            state = State.OK;
        }
        return new ScheduleView(s.getId(), asset.getId(), asset.getTag(), asset.getName(), s.getTitle(), s.getTrade(),
                s.getPriority(), s.getIntervalDays(), hours(s.getIntervalRunningMinutes()),
                s.getLastDoneAt(), AssetDtos.hours(s.getLastDoneRunningMinutes()),
                e.dueAt(), hours(e.dueRunningMinutes()), hours(e.runningMinutesRemaining()),
                state, open == null ? null : new OpenOrder(open.getId(), open.getNumber(), open.isOverdue(now)),
                s.isActive());
    }

    /** Within twice the lead window of generating: worth a caution-yellow on the board. */
    private static boolean approaching(PmSchedule s, PmSchedule.Evaluation e, Instant now) {
        boolean calendar = e.dueAt() != null && s.getIntervalDays() != null
                && !now.isBefore(e.dueAt().minus(PmSchedule.leadOf(Duration.ofDays(s.getIntervalDays())).multipliedBy(2)));
        boolean hours = e.runningMinutesRemaining() != null && s.getIntervalRunningMinutes() != null
                && e.runningMinutesRemaining() <= s.getIntervalRunningMinutes() * 2 * PmSchedule.LEAD_FRACTION;
        return calendar || hours;
    }

    private static BigDecimal hours(Long minutes) {
        return minutes == null ? null : AssetDtos.hours(minutes);
    }

    private static Long toMinutes(BigDecimal hours) {
        return hours == null ? null : hours.multiply(BigDecimal.valueOf(60)).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
