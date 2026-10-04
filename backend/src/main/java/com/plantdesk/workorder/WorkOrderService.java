package com.plantdesk.workorder;

import com.plantdesk.asset.Asset;
import com.plantdesk.asset.AssetRepository;
import com.plantdesk.common.ConflictException;
import com.plantdesk.common.NotFoundException;
import com.plantdesk.part.SparePart;
import com.plantdesk.part.SparePartRepository;
import com.plantdesk.security.AuthenticatedUser;
import com.plantdesk.security.Role;
import com.plantdesk.user.User;
import com.plantdesk.user.UserRepository;
import com.plantdesk.workorder.WorkOrderDtos.Detail;
import com.plantdesk.workorder.WorkOrderDtos.LabourLine;
import com.plantdesk.workorder.WorkOrderDtos.ListItem;
import com.plantdesk.workorder.WorkOrderDtos.PartLine;
import jakarta.persistence.criteria.Predicate;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class WorkOrderService {

    public static final String ANALYTICS_CACHE = "degradingAssets";

    private static final Set<WorkOrderStatus> WORKABLE = EnumSet.of(WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.ON_HOLD);

    private final WorkOrderRepository workOrders;
    private final AssetRepository assets;
    private final UserRepository users;
    private final LabourEntryRepository labour;
    private final PartConsumptionRepository consumptions;
    private final SparePartRepository parts;
    private final WorkOrderNumberGenerator numbers;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public WorkOrderService(WorkOrderRepository workOrders, AssetRepository assets, UserRepository users,
                            LabourEntryRepository labour, PartConsumptionRepository consumptions,
                            SparePartRepository parts, WorkOrderNumberGenerator numbers,
                            ApplicationEventPublisher events, Clock clock) {
        this.workOrders = workOrders;
        this.assets = assets;
        this.users = users;
        this.labour = labour;
        this.consumptions = consumptions;
        this.parts = parts;
        this.numbers = numbers;
        this.events = events;
        this.clock = clock;
    }

    public record ListFilter(Set<WorkOrderStatus> statuses, WorkOrderType type, Priority priority, UUID assetId) {}

    /**
     * No "if technician then filter by assignee" here — and that is the point. For a
     * technician the assignee filter is already enabled on the session (and RLS enforces the
     * same rule underneath), so this exact query returns only their orders.
     */
    @Transactional(readOnly = true)
    public Page<ListItem> list(ListFilter filter, Pageable pageable) {
        Instant now = clock.instant();
        return workOrders.findAll(toSpec(filter), pageable).map(w -> new ListItem(
                w.getId(), w.getNumber(), w.getType(), w.getPriority(), w.getStatus(), w.getTitle(),
                WorkOrderDtos.assetSummary(w.getAsset()), WorkOrderDtos.person(w.getAssignee()),
                w.getRaisedAt(), w.getDueAt(), w.isOverdue(now)));
    }

    @Transactional(readOnly = true)
    public Detail get(UUID id, AuthenticatedUser me) {
        return toDetail(load(id), me);
    }

    @Transactional
    public Detail raise(WorkOrderDtos.RaiseRequest req, AuthenticatedUser me) {
        Instant now = clock.instant();
        Asset asset = assets.findById(req.assetId()).orElseThrow(() -> new NotFoundException("Asset", req.assetId()));
        User assignee;
        if (me.is(Role.TECHNICIAN)) {
            // The shift technician who attends a 2 a.m. trip raises the breakdown themselves
            // and is on it. They cannot raise planned work or hand jobs to colleagues.
            if (req.type() != WorkOrderType.BREAKDOWN) {
                throw new AccessDeniedException("Technicians can raise breakdowns only");
            }
            assignee = users.findById(me.userId()).orElseThrow();
        } else {
            assignee = req.assigneeId() == null ? null : loadTechnician(req.assigneeId());
        }
        String number = numbers.next(now);
        WorkOrder wo = req.type() == WorkOrderType.BREAKDOWN
                ? WorkOrder.breakdown(number, asset, req.priority(), req.title(), req.description(),
                        req.failureCode(), req.failedAt(), me.userId(), now)
                : WorkOrder.preventive(number, asset, req.priority(), req.title(), req.description(), me.userId(), now);
        if (assignee != null) {
            wo.assign(assignee);
        }
        workOrders.save(wo);
        return toDetail(wo, me);
    }

    @Transactional
    public Detail assign(UUID id, UUID technicianId, AuthenticatedUser me) {
        WorkOrder wo = load(id);
        wo.assign(loadTechnician(technicianId));
        return toDetail(wo, me);
    }

    @Transactional
    @CacheEvict(cacheNames = ANALYTICS_CACHE, allEntries = true)
    public Detail transition(UUID id, WorkOrderDtos.TransitionRequest req, AuthenticatedUser me) {
        WorkOrder wo = load(id);
        if (req.action().managerOnly() && me.is(Role.TECHNICIAN)) {
            throw new AccessDeniedException(req.action() + " needs a maintenance manager");
        }
        Instant now = clock.instant();
        wo.apply(req.action(), new WorkOrder.TransitionInput(now, labour.sumMinutes(id), req.note(), req.failureCode()));
        if (req.action() == WorkOrderAction.RETURN_TO_SERVICE && wo.getPmScheduleId() != null) {
            events.publishEvent(new WorkOrderReturnedToService(wo.getId(), wo.getPmScheduleId(), now,
                    wo.getAsset().getRunningMinutes()));
        }
        return toDetail(wo, me);
    }

    @Transactional
    public Detail logLabour(UUID id, WorkOrderDtos.LabourRequest req, AuthenticatedUser me) {
        WorkOrder wo = load(id);
        requireWorkable(wo, "book labour");
        UUID technicianId;
        if (me.is(Role.TECHNICIAN) || req.technicianId() == null) {
            technicianId = me.userId();
        } else {
            technicianId = loadTechnician(req.technicianId()).getId();
        }
        labour.save(new LabourEntry(wo.getId(), technicianId, req.minutes(), req.workDate(), req.note(), clock.instant()));
        return toDetail(wo, me);
    }

    @Transactional
    public Detail consumePart(UUID id, WorkOrderDtos.PartRequest req, AuthenticatedUser me) {
        WorkOrder wo = load(id);
        requireWorkable(wo, "draw spares");
        SparePart part = parts.findById(req.sparePartId())
                .orElseThrow(() -> new NotFoundException("Spare part", req.sparePartId()));
        if (parts.issue(part.getId(), req.quantity()) == 0) {
            throw new ConflictException("Only " + part.getStockQty().stripTrailingZeros().toPlainString() + " "
                    + part.getUnit() + " of " + part.getPartNumber() + " in stock; cannot issue "
                    + req.quantity().stripTrailingZeros().toPlainString() + ".");
        }
        consumptions.save(new PartConsumption(wo.getId(), part.getId(), req.quantity(), part.getUnitCost(),
                clock.instant(), me.userId()));
        return toDetail(wo, me);
    }

    @Transactional
    @CacheEvict(cacheNames = ANALYTICS_CACHE, allEntries = true)
    public Detail correctDowntime(UUID id, WorkOrderDtos.DowntimeRequest req, AuthenticatedUser me) {
        WorkOrder wo = load(id);
        wo.correctDowntime(req.start(), req.end());
        return toDetail(wo, me);
    }

    private WorkOrder load(UUID id) {
        return workOrders.findDetailed(id).orElseThrow(() -> new NotFoundException("Work order", id));
    }

    private User loadTechnician(UUID id) {
        User u = users.findById(id).orElseThrow(() -> new NotFoundException("Technician", id));
        if (u.getRole() != Role.TECHNICIAN || !u.isActive()) {
            throw new ConflictException(u.getFullName() + " is not an active technician");
        }
        return u;
    }

    private static void requireWorkable(WorkOrder wo, String what) {
        if (!WORKABLE.contains(wo.getStatus())) {
            throw new InvalidTransitionException("Cannot " + what + " on " + wo.getNumber() + ": it is "
                    + wo.getStatus() + ". Start the job first.");
        }
    }

    private Detail toDetail(WorkOrder wo, AuthenticatedUser me) {
        Instant now = clock.instant();
        List<LabourEntry> labourEntries = labour.findByWorkOrderIdOrderByWorkDateAscIdAsc(wo.getId());
        List<PartConsumption> partEntries = consumptions.findByWorkOrderIdOrderByConsumedAtAsc(wo.getId());

        Map<UUID, User> people = labourEntries.isEmpty() ? Map.of() : users.findAllById(
                labourEntries.stream().map(LabourEntry::getTechnicianId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<UUID, SparePart> partById = partEntries.isEmpty() ? Map.of() : parts.findAllById(
                partEntries.stream().map(PartConsumption::getSparePartId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(SparePart::getId, Function.identity()));

        List<LabourLine> labourLines = labourEntries.stream().map(l -> new LabourLine(l.getId(),
                WorkOrderDtos.person(people.get(l.getTechnicianId())), l.getMinutes(), l.getWorkDate(), l.getNote())).toList();
        List<PartLine> partLines = partEntries.stream().map(p -> {
            SparePart sp = partById.get(p.getSparePartId());
            return new PartLine(p.getId(), p.getSparePartId(), sp == null ? null : sp.getPartNumber(),
                    sp == null ? null : sp.getDescription(), sp == null ? null : sp.getUnit().name(),
                    p.getQuantity(), p.getUnitCost(), p.lineCost(), p.getConsumedAt());
        }).toList();

        long labourTotal = labourEntries.stream().mapToLong(LabourEntry::getMinutes).sum();
        BigDecimal partsTotal = partEntries.stream().map(PartConsumption::lineCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        Long downtimeMinutes = wo.getDowntimeStart() == null ? null : Duration.between(wo.getDowntimeStart(),
                wo.getDowntimeEnd() == null ? now : wo.getDowntimeEnd()).toMinutes();

        return new Detail(wo.getId(), wo.getNumber(), wo.getType(), wo.getPriority(), wo.getStatus(), wo.getTitle(),
                wo.getDescription(), WorkOrderDtos.failureCode(wo.getFailureCode()),
                WorkOrderDtos.assetSummary(wo.getAsset()), WorkOrderDtos.person(wo.getAssignee()),
                wo.getRaisedAt(), wo.getStartedAt(), wo.getCompletedAt(), wo.getClosedAt(),
                wo.getDowntimeStart(), wo.getDowntimeEnd(), downtimeMinutes,
                wo.getHoldReason(), wo.getClosureNote(),
                wo.getPmScheduleId(), wo.getDueAt(), WorkOrderDtos.hours(wo.getDueRunningMinutes()), wo.isOverdue(now),
                labourLines, labourTotal, partLines, partsTotal, actionsFor(wo, me));
    }

    /** What this user may do next — the UI renders exactly these buttons and no others. */
    private static Set<WorkOrderAction> actionsFor(WorkOrder wo, AuthenticatedUser me) {
        if (me.is(Role.VIEWER)) {
            return Set.of();
        }
        Set<WorkOrderAction> actions = wo.availableActions();
        if (me.is(Role.TECHNICIAN)) {
            actions.removeIf(WorkOrderAction::managerOnly);
        }
        return actions;
    }

    private static Specification<WorkOrder> toSpec(ListFilter f) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (f.statuses() != null && !f.statuses().isEmpty()) {
                p.add(root.get("status").in(f.statuses()));
            }
            if (f.type() != null) {
                p.add(cb.equal(root.get("type"), f.type()));
            }
            if (f.priority() != null) {
                p.add(cb.equal(root.get("priority"), f.priority()));
            }
            if (f.assetId() != null) {
                p.add(cb.equal(root.get("asset").get("id"), f.assetId()));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}
