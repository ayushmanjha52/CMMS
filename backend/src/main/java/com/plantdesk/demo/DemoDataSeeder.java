package com.plantdesk.demo;

import com.plantdesk.asset.Asset;
import com.plantdesk.asset.AssetDtos.AssetDetail;
import com.plantdesk.asset.AssetDtos.CreateAssetRequest;
import com.plantdesk.asset.AssetLevel;
import com.plantdesk.asset.AssetService;
import com.plantdesk.asset.Criticality;
import com.plantdesk.asset.MeterReading;
import com.plantdesk.asset.MeterReadingRepository;
import com.plantdesk.part.SparePart;
import com.plantdesk.part.SparePartRepository;
import com.plantdesk.part.UnitOfMeasure;
import com.plantdesk.pm.PmGenerationService;
import com.plantdesk.pm.PmSchedule;
import com.plantdesk.pm.PmScheduleRepository;
import com.plantdesk.security.Role;
import com.plantdesk.tenancy.SystemLookupDao;
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
import com.plantdesk.workorder.PartConsumption;
import com.plantdesk.workorder.PartConsumptionRepository;
import com.plantdesk.workorder.Priority;
import com.plantdesk.workorder.WorkOrder;
import com.plantdesk.workorder.WorkOrderAction;
import com.plantdesk.workorder.WorkOrderNumberGenerator;
import com.plantdesk.workorder.WorkOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Seeds a demo thermal power station (plant code DEMO) and a small electric loco shed
 * (plant code LOCO) so anyone can log in and see isolation between two tenants.
 * Idempotent: does nothing if DEMO already exists.
 */
@Component
@ConditionalOnProperty(name = "plantdesk.demo.enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    public static final String DEMO_PASSWORD = "plantdesk-demo";
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final SystemLookupDao lookup;
    private final TransactionTemplate tx;
    private final TenantRepository tenants;
    private final UserRepository users;
    private final AssetService assetService;
    private final MeterReadingRepository readings;
    private final SparePartRepository parts;
    private final WorkOrderRepository workOrders;
    private final WorkOrderNumberGenerator numbers;
    private final LabourEntryRepository labour;
    private final PartConsumptionRepository consumptions;
    private final PmScheduleRepository schedules;
    private final PmGenerationService pmGeneration;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public DemoDataSeeder(SystemLookupDao lookup, TransactionTemplate tx, TenantRepository tenants, UserRepository users,
                          AssetService assetService, MeterReadingRepository readings, SparePartRepository parts,
                          WorkOrderRepository workOrders, WorkOrderNumberGenerator numbers, LabourEntryRepository labour,
                          PartConsumptionRepository consumptions, PmScheduleRepository schedules,
                          PmGenerationService pmGeneration, PasswordEncoder passwordEncoder, Clock clock) {
        this.lookup = lookup;
        this.tx = tx;
        this.tenants = tenants;
        this.users = users;
        this.assetService = assetService;
        this.readings = readings;
        this.parts = parts;
        this.workOrders = workOrders;
        this.numbers = numbers;
        this.labour = labour;
        this.consumptions = consumptions;
        this.schedules = schedules;
        this.pmGeneration = pmGeneration;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /** Seeded logins on the public demo. They cannot be deactivated, so no visitor can lock out the next. */
    public static boolean isDemoAccount(String email) {
        return email.endsWith("@demo.plant") || email.endsWith("@loco.shed");
    }

    @Override
    public void run(ApplicationArguments args) {
        seedIfMissing();
    }

    public void seedIfMissing() {
        if (lookup.findTenantIdByCode("DEMO").isPresent()) {
            return;
        }
        UUID demo = UUID.randomUUID();
        TenantContext.runAs(TenantScope.system(demo), () -> tx.executeWithoutResult(s -> seedThermalStation(demo)));
        UUID loco = UUID.randomUUID();
        TenantContext.runAs(TenantScope.system(loco), () -> tx.executeWithoutResult(s -> seedLocoShed(loco)));
        log.info("Demo tenants seeded: DEMO and LOCO (password '{}')", DEMO_PASSWORD);
    }

    // ------------------------------------------------------------------------------------
    // DEMO — thermal power station
    // ------------------------------------------------------------------------------------

    private void seedThermalStation(UUID tenantId) {
        Instant now = clock.instant();
        tenants.save(new Tenant(tenantId, "DEMO", "Demo Thermal Power Station", now));

        String hash = passwordEncoder.encode(DEMO_PASSWORD);
        User admin = users.save(new User("admin@demo.plant", hash, "Anita Deshmukh", Role.PLANT_ADMIN, null, Shift.GENERAL, now));
        User manager = users.save(new User("manager@demo.plant", hash, "Suresh Patil", Role.MAINTENANCE_MANAGER, Trade.MECHANICAL, Shift.GENERAL, now));
        User elec = users.save(new User("electrical@demo.plant", hash, "Imran Shaikh", Role.TECHNICIAN, Trade.ELECTRICAL, Shift.A, now));
        User mech = users.save(new User("mechanical@demo.plant", hash, "Rahul Yadav", Role.TECHNICIAN, Trade.MECHANICAL, Shift.B, now));
        User inst = users.save(new User("instrument@demo.plant", hash, "Kavya Iyer", Role.TECHNICIAN, Trade.INSTRUMENTATION, Shift.GENERAL, now));
        users.save(new User("viewer@demo.plant", hash, "Vikram Rao", Role.VIEWER, null, Shift.GENERAL, now));

        LocalDate commissioned = LocalDate.of(2019, 4, 1);
        AssetDetail plant = asset(null, AssetLevel.PLANT, "Unit-1 210 MW", null, null, null, null, null, Criticality.A, commissioned);
        AssetDetail boiler = asset(plant, AssetLevel.AREA, "Boiler", null, null, null, null, null, Criticality.A, commissioned);
        AssetDetail turbine = asset(plant, AssetLevel.AREA, "Turbine & Generator", null, null, null, null, null, Criticality.A, commissioned);
        AssetDetail chp = asset(plant, AssetLevel.AREA, "Coal Handling Plant", null, null, null, null, null, Criticality.A, commissioned);
        AssetDetail wtp = asset(plant, AssetLevel.AREA, "Water Treatment Plant", null, null, null, null, null, Criticality.B, commissioned);

        AssetDetail idFanLine = asset(boiler, AssetLevel.LINE, "ID Fan System", null, null, null, null, null, Criticality.A, commissioned);
        AssetDetail fdFanLine = asset(boiler, AssetLevel.LINE, "FD Fan System", null, null, null, null, null, Criticality.A, commissioned);
        AssetDetail millLine = asset(boiler, AssetLevel.LINE, "Coal Mills", null, null, null, null, null, Criticality.A, commissioned);
        AssetDetail bfpLine = asset(turbine, AssetLevel.LINE, "Boiler Feed Pumps", null, null, null, null, null, Criticality.A, commissioned);
        AssetDetail conv1a = asset(chp, AssetLevel.LINE, "Conveyor 1A", null, null, null, null, null, Criticality.A, commissioned);
        AssetDetail conv1b = asset(chp, AssetLevel.LINE, "Conveyor 1B (standby)", null, null, null, null, null, Criticality.B, commissioned);

        AssetDetail idFanMotor = asset(idFanLine, AssetLevel.MACHINE, "ID Fan-A Motor", "MTR", "BHEL", "1LA7 6.6kV", "1500 kW, 6.6 kV, 990 rpm", "BH-6612-0457", Criticality.A, commissioned);
        asset(idFanLine, AssetLevel.MACHINE, "ID Fan-A", "FAN", "BHEL", "NDZV 31", "Radial, 2x50%", "IDF-A-2019-01", Criticality.A, commissioned);
        AssetDetail fdFanMotor = asset(fdFanLine, AssetLevel.MACHINE, "FD Fan-A Motor", "MTR", "BHEL", "1LA7 6.6kV", "900 kW, 6.6 kV, 1490 rpm", "BH-6609-0332", Criticality.A, commissioned);
        AssetDetail millA = asset(millLine, AssetLevel.MACHINE, "Coal Mill-A", "MIL", "BHEL", "XRP 783", "Bowl mill, 35 t/h", "MIL-A-0783", Criticality.A, commissioned);
        AssetDetail millAMotor = asset(millLine, AssetLevel.MACHINE, "Coal Mill-A Motor", "MTR", "Crompton Greaves", "HV 6.6kV", "340 kW, 6.6 kV, 990 rpm", "CG-34-11872", Criticality.A, commissioned);
        AssetDetail bfpA = asset(bfpLine, AssetLevel.MACHINE, "BFP-A", "PMP", "KSB", "CHTD 6/5", "Multistage, 430 m3/h", "KSB-BFP-0091", Criticality.A, commissioned);
        AssetDetail bfpAMotor = asset(bfpLine, AssetLevel.MACHINE, "BFP-A Motor", "MTR", "BHEL", "1RA6 6.6kV", "3500 kW, 6.6 kV, 1490 rpm", "BH-6635-0118", Criticality.A, commissioned);
        AssetDetail conv1aMotor = asset(conv1a, AssetLevel.MACHINE, "Conveyor 1A Drive Motor", "MTR", "Crompton Greaves", "LV 415V", "160 kW, 415 V, 1485 rpm", "CG-16-20931", Criticality.A, commissioned);
        AssetDetail conv1aGbx = asset(conv1a, AssetLevel.MACHINE, "Conveyor 1A Gearbox", "GBX", "Elecon", "HU 280", "Ratio 31.5:1", "EL-HU280-5530", Criticality.A, commissioned);
        asset(conv1aMotor, AssetLevel.COMPONENT, "DE Bearing, Conv 1A Motor", "BRG", "SKF", "6319 C3", "Deep groove, 95 mm bore", null, Criticality.A, commissioned);
        AssetDetail conv1bMotor = asset(conv1b, AssetLevel.MACHINE, "Conveyor 1B Drive Motor", "MTR", "Crompton Greaves", "LV 415V", "160 kW, 415 V, 1485 rpm", "CG-16-20932", Criticality.B, commissioned);
        // WTP equipment sits straight under the area — no "line" in a water treatment plant.
        AssetDetail dmPump = asset(wtp, AssetLevel.MACHINE, "DM Water Transfer Pump-1", "PMP", "KSB", "Etanorm 65-200", "60 m3/h @ 50 m", "KSB-ETN-7731", Criticality.B, commissioned);
        AssetDetail clarifier = asset(wtp, AssetLevel.MACHINE, "Clarifier Drive", "MTR", "Crompton Greaves", "LV 415V", "7.5 kW, 415 V", "CG-07-55120", Criticality.C, commissioned);

        meter(idFanMotor, 41_250, admin);
        meter(bfpAMotor, 39_880, admin);
        meter(conv1aMotor, 28_410, admin);
        meter(conv1aGbx, 28_410, admin);
        meter(millAMotor, 33_060, admin);
        meter(dmPump, 21_940, admin);

        SparePart bearing = parts.save(new SparePart("SKF-6319-C3", "Deep groove ball bearing 6319 C3", UnitOfMeasure.EA,
                new BigDecimal("18450.00"), new BigDecimal("2"), "R-04-B2", now));
        bearing.receive(new BigDecimal("6"), null);
        SparePart oil = parts.save(new SparePart("OIL-VG320", "Gear oil ISO VG 320", UnitOfMeasure.L,
                new BigDecimal("312.50"), new BigDecimal("100"), "OIL-STORE", now));
        oil.receive(new BigDecimal("420"), null);
        SparePart grease = parts.save(new SparePart("GRS-EP2", "Lithium complex grease EP2", UnitOfMeasure.KG,
                new BigDecimal("640.00"), new BigDecimal("10"), "OIL-STORE", now));
        grease.receive(new BigDecimal("8"), null); // below reorder point on purpose
        SparePart seal = parts.save(new SparePart("MS-ETN65", "Mechanical seal, Etanorm 65-200", UnitOfMeasure.SET,
                new BigDecimal("27800.00"), new BigDecimal("1"), "R-02-A1", now));
        seal.receive(new BigDecimal("2"), null);
        SparePart contactor = parts.save(new SparePart("CNT-95A", "Power contactor 95 A, 415 V AC3", UnitOfMeasure.EA,
                new BigDecimal("8900.00"), new BigDecimal("2"), "E-STORE-3", now));
        contactor.receive(new BigDecimal("5"), null);
        SparePart cable = parts.save(new SparePart("CBL-3C95", "LT power cable 3C x 95 sq mm Al", UnitOfMeasure.M,
                new BigDecimal("610.00"), new BigDecimal("50"), "YARD-1", now));
        cable.receive(new BigDecimal("240"), null);

        // --- Twelve months of breakdowns. Conveyor 1A motor's gaps shrink 90 → 12 days: the
        // health strip shows the ticks bunching toward the right and analytics flags it.
        List<Breakdown> history = new ArrayList<>();
        int[][] conv = {{340, 4}, {250, 5}, {175, 3}, {115, 6}, {70, 4}, {38, 7}, {18, 5}, {6, 8}};
        FailureCode[] convCodes = {FailureCode.M03, FailureCode.M01, FailureCode.E02, FailureCode.M01,
                FailureCode.M03, FailureCode.M01, FailureCode.E02, FailureCode.M01};
        for (int i = 0; i < conv.length; i++) {
            history.add(new Breakdown(conv1aMotor, conv[i][0], conv[i][1], convCodes[i],
                    i >= 5 ? Priority.EMERGENCY : Priority.HIGH, "Conveyor 1A tripped — " + convCodes[i].description().toLowerCase(),
                    convCodes[i] == FailureCode.M01 ? mech : elec, convCodes[i] == FailureCode.M01 ? bearing : null));
        }
        // BFP-A: steady 90-day gaps — stable.
        for (int d : new int[]{330, 240, 150, 60}) {
            history.add(new Breakdown(bfpA, d, 6, FailureCode.M04, Priority.HIGH, "BFP-A gland leakage", mech, null));
        }
        history.add(new Breakdown(idFanMotor, 300, 9, FailureCode.E01, Priority.EMERGENCY, "ID Fan-A motor earth fault trip", elec, cable));
        history.add(new Breakdown(idFanMotor, 160, 3, FailureCode.E03, Priority.HIGH, "ID Fan-A motor breaker not closing", elec, contactor));
        history.add(new Breakdown(idFanMotor, 40, 2, FailureCode.I01, Priority.HIGH, "ID Fan-A bearing temperature RTD faulty", inst, null));
        // Supply dip, not the motor's fault — excluded from MTBF by failure code X01.
        history.add(new Breakdown(fdFanMotor, 95, 1, FailureCode.X01, Priority.HIGH, "FD Fan-A tripped on 6.6 kV bus undervoltage", elec, null));
        history.add(new Breakdown(millAMotor, 210, 5, FailureCode.E04, Priority.EMERGENCY, "Mill-A motor cable termination burnt", elec, cable));
        history.add(new Breakdown(dmPump, 120, 4, FailureCode.M04, Priority.MEDIUM, "DM pump-1 seal leakage", mech, seal));
        history.sort(Comparator.comparingInt(Breakdown::daysAgo).reversed());
        for (Breakdown b : history) {
            closedBreakdown(b, manager, now);
        }

        // --- Live jobs for today's board.
        Asset millAsset = assetService.load(millA.id());
        Instant millFailed = now.minus(Duration.ofHours(5));
        WorkOrder millJob = WorkOrder.breakdown(numbers.next(millFailed), millAsset, Priority.EMERGENCY,
                "Mill-A lube oil pump not starting", "Lube oil pressure low interlock. Mill tripped on startup.",
                FailureCode.E03, millFailed, elec.getId(), millFailed.plus(Duration.ofMinutes(10)));
        millJob.assign(elec);
        workOrders.save(millJob);
        millJob.apply(WorkOrderAction.START, new WorkOrder.TransitionInput(millFailed.plus(Duration.ofMinutes(25)), 0, null, null));
        labour.save(new LabourEntry(millJob.getId(), elec.getId(), 150, today(now), "Checked MCC feeder; contactor coil open", now));

        Asset dmAsset = assetService.load(dmPump.id());
        Instant dmFailed = now.minus(Duration.ofHours(2));
        WorkOrder dmJob = WorkOrder.breakdown(numbers.next(dmFailed), dmAsset, Priority.HIGH,
                "DM pump-1 seal leaking again", "Leak rate visibly increasing. Standby pump-2 in service.",
                null, dmFailed, manager.getId(), dmFailed.plus(Duration.ofMinutes(15)));
        workOrders.save(dmJob);

        Asset clarifierAsset = assetService.load(clarifier.id());
        WorkOrder clarifierJob = WorkOrder.preventive(numbers.next(now), clarifierAsset, Priority.LOW,
                "Clarifier drive gearbox oil top-up", "Check level, top up VG 320.", manager.getId(), now);
        clarifierJob.assign(mech);
        workOrders.save(clarifierJob);

        // --- PM schedules. Baselines chosen so the board shows every state.
        Asset idFanMotorAsset = assetService.load(idFanMotor.id());
        Asset conv1aGbxAsset = assetService.load(conv1aGbx.id());
        Asset bfpAMotorAsset = assetService.load(bfpAMotor.id());
        Asset conv1bMotorAsset = assetService.load(conv1bMotor.id());
        // Overdue: greasing due 30 days after it was last done 36 days ago.
        schedules.save(new PmSchedule(idFanMotorAsset, "ID Fan-A motor bearing greasing",
                "Purge old grease. 60 g EP2 per bearing. Record bearing temperature before and after.",
                Trade.MECHANICAL, Priority.HIGH, 30, 720L * 60, now.minus(Duration.ofDays(36)), (41_250L - 700) * 60, now));
        // Hours-triggered: gearbox oil change due at 2000 h; 1950 h run since last change.
        schedules.save(new PmSchedule(conv1aGbxAsset, "Conveyor 1A gearbox oil change",
                "Drain hot. Flush. Refill 85 L ISO VG 320. Check magnetic plug for debris.",
                Trade.MECHANICAL, Priority.MEDIUM, 180, 2000L * 60, now.minus(Duration.ofDays(70)), (28_410L - 1_950) * 60, now));
        // Approaching: monthly IR test, last done 25 days ago.
        schedules.save(new PmSchedule(bfpAMotorAsset, "BFP-A motor insulation resistance test",
                "5 kV megger, 1 min and 10 min readings, compute PI. PI < 2 to be reported.",
                Trade.ELECTRICAL, Priority.MEDIUM, 30, null, now.minus(Duration.ofDays(25)), 0, now));
        // OK: standby motor, calendar only.
        schedules.save(new PmSchedule(conv1bMotorAsset, "Conveyor 1B standby motor trial run",
                "Run 30 min on no load. Check vibration and winding temperature.",
                Trade.ELECTRICAL, Priority.LOW, 15, null, now.minus(Duration.ofDays(3)), 0, now));
        schedules.flush();
        pmGeneration.generateDue();

        // The overdue greasing PM has been sitting unassigned; give it to the B-shift fitter.
        workOrders.findPmOrdersWithStatusIn(List.of(com.plantdesk.workorder.WorkOrderStatus.OPEN))
                .forEach(w -> w.assign(mech));
    }

    // ------------------------------------------------------------------------------------
    // LOCO — electric loco shed (second tenant for the isolation demo)
    // ------------------------------------------------------------------------------------

    private void seedLocoShed(UUID tenantId) {
        Instant now = clock.instant();
        tenants.save(new Tenant(tenantId, "LOCO", "Demo Electric Loco Shed", now));
        String hash = passwordEncoder.encode(DEMO_PASSWORD);
        User admin = users.save(new User("admin@loco.shed", hash, "Prakash Meena", Role.PLANT_ADMIN, null, Shift.GENERAL, now));
        User tech = users.save(new User("electrical@loco.shed", hash, "Deepak Verma", Role.TECHNICIAN, Trade.ELECTRICAL, Shift.A, now));

        LocalDate commissioned = LocalDate.of(2016, 7, 1);
        AssetDetail shed = asset(null, AssetLevel.PLANT, "Electric Loco Shed", null, null, null, null, null, Criticality.A, commissioned);
        AssetDetail bay = asset(shed, AssetLevel.AREA, "Traction Motor Bay", null, null, null, null, null, Criticality.A, commissioned);
        AssetDetail pitLine = asset(shed, AssetLevel.AREA, "Inspection Pit Line", null, null, null, null, null, Criticality.B, commissioned);
        AssetDetail tm = asset(bay, AssetLevel.MACHINE, "Traction Motor TM-3, Loco 30412", "TM", "CLW", "6FRA 6068", "630 kW, 2180 V", "TM-6068-3341", Criticality.A, commissioned);
        AssetDetail eot = asset(pitLine, AssetLevel.MACHINE, "EOT Crane 10 t", "CRN", "Anupam", "DG-10", "10 t, 18 m span", "ANP-10-778", Criticality.B, commissioned);
        meter(tm, 18_200, admin);

        closedBreakdown(new Breakdown(tm, 45, 10, FailureCode.E01, Priority.EMERGENCY,
                "TM-3 earth fault on traction", tech, null), admin, now);
        Asset crane = assetService.load(eot.id());
        WorkOrder craneJob = WorkOrder.breakdown(numbers.next(now), crane, Priority.HIGH,
                "EOT crane long-travel brake not releasing", null, null, now.minus(Duration.ofHours(1)), admin.getId(), now);
        craneJob.assign(tech);
        workOrders.save(craneJob);
    }

    // ------------------------------------------------------------------------------------

    private record Breakdown(AssetDetail asset, int daysAgo, int downtimeHours, FailureCode code, Priority priority,
                             String title, User technician, SparePart part) {}

    private void closedBreakdown(Breakdown b, User manager, Instant now) {
        Asset asset = assetService.load(b.asset().id());
        Instant failed = now.minus(Duration.ofDays(b.daysAgo())).truncatedTo(ChronoUnit.MINUTES).minus(Duration.ofHours(3));
        Instant raised = failed.plus(Duration.ofMinutes(12));
        Instant restored = failed.plus(Duration.ofHours(b.downtimeHours()));
        WorkOrder wo = WorkOrder.breakdown(numbers.next(raised), asset, b.priority(), b.title(), null,
                b.code(), failed, manager.getId(), raised);
        wo.assign(b.technician());
        workOrders.save(wo);
        wo.apply(WorkOrderAction.START, new WorkOrder.TransitionInput(raised.plus(Duration.ofMinutes(20)), 0, null, null));
        int minutes = (int) Math.min(1440, Math.max(30, Duration.between(raised, restored).toMinutes() - 15));
        labour.save(new LabourEntry(wo.getId(), b.technician().getId(), minutes,
                restored.atZone(ZoneOffset.UTC).toLocalDate(), "Attended and rectified", restored));
        if (b.part() != null) {
            BigDecimal qty = b.part().getUnit() == UnitOfMeasure.M ? new BigDecimal("12") : BigDecimal.ONE;
            if (parts.issue(b.part().getId(), qty) == 1) {
                consumptions.save(new PartConsumption(wo.getId(), b.part().getId(), qty, b.part().getUnitCost(),
                        restored.minus(Duration.ofMinutes(30)), b.technician().getId()));
            }
        }
        wo.apply(WorkOrderAction.RETURN_TO_SERVICE, new WorkOrder.TransitionInput(restored, minutes, null, null));
        wo.apply(WorkOrderAction.CLOSE, new WorkOrder.TransitionInput(restored.plus(Duration.ofHours(20)), minutes,
                "Reviewed. Job card complete.", null));
    }

    private AssetDetail asset(AssetDetail parent, AssetLevel level, String name, String typePrefix, String make,
                              String model, String rating, String serial, Criticality criticality, LocalDate commissioned) {
        return assetService.create(new CreateAssetRequest(parent == null ? null : parent.id(), level, name, typePrefix,
                make, model, rating, serial, criticality, commissioned));
    }

    private void meter(AssetDetail detail, long hours, User by) {
        Asset asset = assetService.load(detail.id());
        Instant now = clock.instant();
        asset.recordRunningMinutes(hours * 60, now);
        readings.save(new MeterReading(asset.getId(), hours * 60, now, by.getId()));
    }

    private static LocalDate today(Instant now) {
        return now.atZone(ZoneOffset.UTC).toLocalDate();
    }
}
