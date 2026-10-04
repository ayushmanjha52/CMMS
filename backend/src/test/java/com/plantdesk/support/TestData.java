package com.plantdesk.support;

import com.plantdesk.asset.Asset;
import com.plantdesk.asset.AssetDtos.CreateAssetRequest;
import com.plantdesk.asset.AssetLevel;
import com.plantdesk.asset.AssetService;
import com.plantdesk.asset.Criticality;
import com.plantdesk.part.SparePart;
import com.plantdesk.part.SparePartRepository;
import com.plantdesk.part.UnitOfMeasure;
import com.plantdesk.security.Role;
import com.plantdesk.tenancy.TenantContext;
import com.plantdesk.tenancy.TenantScope;
import com.plantdesk.tenant.Tenant;
import com.plantdesk.tenant.TenantRepository;
import com.plantdesk.user.Shift;
import com.plantdesk.user.Trade;
import com.plantdesk.user.User;
import com.plantdesk.user.UserRepository;
import com.plantdesk.workorder.FailureCode;
import com.plantdesk.workorder.LabourEntry;
import com.plantdesk.workorder.LabourEntryRepository;
import com.plantdesk.workorder.Priority;
import com.plantdesk.workorder.WorkOrder;
import com.plantdesk.workorder.WorkOrderAction;
import com.plantdesk.workorder.WorkOrderNumberGenerator;
import com.plantdesk.workorder.WorkOrderRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.Supplier;

/** Builds tenants and their data through the real domain code, under the right tenant scope. */
@Component
public class TestData {

    public static final String PASSWORD = "correct-horse-battery";

    private final TransactionTemplate tx;
    private final TenantRepository tenants;
    private final UserRepository users;
    private final AssetService assets;
    private final SparePartRepository parts;
    private final WorkOrderRepository workOrders;
    private final WorkOrderNumberGenerator numbers;
    private final LabourEntryRepository labour;
    private final Clock clock;
    private final String passwordHash;

    public TestData(TransactionTemplate tx, TenantRepository tenants, UserRepository users, AssetService assets,
                    SparePartRepository parts, WorkOrderRepository workOrders, WorkOrderNumberGenerator numbers,
                    LabourEntryRepository labour, Clock clock, PasswordEncoder encoder) {
        this.tx = tx;
        this.tenants = tenants;
        this.users = users;
        this.assets = assets;
        this.parts = parts;
        this.workOrders = workOrders;
        this.numbers = numbers;
        this.labour = labour;
        this.clock = clock;
        this.passwordHash = encoder.encode(PASSWORD);
    }

    public record Plant(UUID tenantId, String code, User admin, User manager, User tech1, User tech2, User viewer,
                        UUID plantAssetId, UUID areaId, UUID machineId) {}

    public Plant newPlant() {
        UUID tenantId = UUID.randomUUID();
        String code = "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return as(tenantId, () -> {
            Instant now = clock.instant();
            tenants.save(new Tenant(tenantId, code, "Test plant " + code, now));
            String domain = code.toLowerCase() + ".test";
            User admin = users.save(new User("admin@" + domain, passwordHash, "Admin " + code, Role.PLANT_ADMIN, null, Shift.GENERAL, now));
            User manager = users.save(new User("manager@" + domain, passwordHash, "Manager " + code, Role.MAINTENANCE_MANAGER, Trade.MECHANICAL, Shift.GENERAL, now));
            User tech1 = users.save(new User("tech1@" + domain, passwordHash, "Tech One " + code, Role.TECHNICIAN, Trade.ELECTRICAL, Shift.A, now));
            User tech2 = users.save(new User("tech2@" + domain, passwordHash, "Tech Two " + code, Role.TECHNICIAN, Trade.MECHANICAL, Shift.B, now));
            User viewer = users.save(new User("viewer@" + domain, passwordHash, "Viewer " + code, Role.VIEWER, null, Shift.GENERAL, now));
            UUID plant = create(null, AssetLevel.PLANT, "Plant", null, null);
            UUID area = create(plant, AssetLevel.AREA, "Area", null, null);
            UUID machine = create(area, AssetLevel.MACHINE, "Machine", "MTR", null);
            return new Plant(tenantId, code, admin, manager, tech1, tech2, viewer, plant, area, machine);
        });
    }

    public UUID machine(Plant p, String name, LocalDate commissionedOn) {
        return as(p.tenantId(), () -> create(p.areaId(), AssetLevel.MACHINE, name, "MTR", commissionedOn));
    }

    public UUID openBreakdown(Plant p, UUID assetId, User assignee) {
        return as(p.tenantId(), () -> {
            Instant now = clock.instant();
            Asset asset = assets.load(assetId);
            WorkOrder wo = WorkOrder.breakdown(numbers.next(now), asset, Priority.HIGH, "Breakdown on " + asset.getTag(),
                    null, null, now, p.manager().getId(), now);
            if (assignee != null) {
                wo.assign(users.findById(assignee.getId()).orElseThrow());
            }
            return workOrders.save(wo).getId();
        });
    }

    /** A breakdown that has been through the whole lifecycle, with its downtime window as given. */
    public UUID closedBreakdown(Plant p, UUID assetId, Instant failedAt, Duration downtime, FailureCode code) {
        return as(p.tenantId(), () -> {
            Asset asset = assets.load(assetId);
            Instant restored = failedAt.plus(downtime);
            WorkOrder wo = WorkOrder.breakdown(numbers.next(failedAt), asset, Priority.HIGH, "Failure " + code,
                    null, code, failedAt, p.manager().getId(), failedAt);
            wo.assign(users.findById(p.tech1().getId()).orElseThrow());
            workOrders.save(wo);
            wo.apply(WorkOrderAction.START, new WorkOrder.TransitionInput(failedAt, 0, null, null));
            labour.save(new LabourEntry(wo.getId(), p.tech1().getId(), 60,
                    failedAt.atZone(ZoneOffset.UTC).toLocalDate(), null, failedAt));
            wo.apply(WorkOrderAction.RETURN_TO_SERVICE, new WorkOrder.TransitionInput(restored, 60, null, null));
            wo.apply(WorkOrderAction.CLOSE, new WorkOrder.TransitionInput(restored, 60, null, null));
            return wo.getId();
        });
    }

    public UUID part(Plant p, String partNumber, String stock, String unitCost) {
        return as(p.tenantId(), () -> {
            SparePart part = new SparePart(partNumber, "Part " + partNumber, UnitOfMeasure.EA, new BigDecimal(unitCost),
                    BigDecimal.ONE, null, clock.instant());
            part.receive(new BigDecimal(stock), null);
            return parts.save(part).getId();
        });
    }

    public UUID create(UUID parentId, AssetLevel level, String name, String typePrefix, LocalDate commissionedOn) {
        return assets.create(new CreateAssetRequest(parentId, level, name, typePrefix, "Make", "Model", "Rating",
                "SN-" + UUID.randomUUID().toString().substring(0, 6), Criticality.A, commissionedOn)).id();
    }

    /** Runs work in its own transaction under a tenant's system scope. */
    public <T> T as(UUID tenantId, Supplier<T> work) {
        return TenantContext.callAs(TenantScope.system(tenantId), () -> tx.execute(status -> work.get()));
    }

    /** Binds a real user's scope without opening a transaction — for calling @Transactional services. */
    public <T> T asUser(User user, Supplier<T> work) {
        return TenantContext.callAs(new TenantScope(user.getTenantId(), user.getId(), user.getRole()), work);
    }

    public com.plantdesk.security.AuthenticatedUser principal(User u) {
        return new com.plantdesk.security.AuthenticatedUser(u.getId(), u.getTenantId(), u.getRole(), u.getEmail(), u.getFullName());
    }

    public void run(UUID tenantId, Runnable work) {
        as(tenantId, () -> {
            work.run();
            return null;
        });
    }
}
