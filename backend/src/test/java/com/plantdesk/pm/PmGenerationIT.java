package com.plantdesk.pm;

import com.plantdesk.asset.AssetDtos.MeterReadingRequest;
import com.plantdesk.asset.AssetService;
import com.plantdesk.support.AbstractIntegrationTest;
import com.plantdesk.support.TestData.Plant;
import com.plantdesk.user.Trade;
import com.plantdesk.workorder.Priority;
import com.plantdesk.workorder.WorkOrder;
import com.plantdesk.workorder.WorkOrderAction;
import com.plantdesk.workorder.WorkOrderDtos;
import com.plantdesk.workorder.WorkOrderNumberGenerator;
import com.plantdesk.workorder.WorkOrderRepository;
import com.plantdesk.workorder.WorkOrderService;
import com.plantdesk.workorder.WorkOrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Time is a dependency, so it is injected — and here it is moved by hand. Each step says
 * exactly how many PM work orders should exist; a duplicate fails the count.
 */
class PmGenerationIT extends AbstractIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-01-05T06:00:00Z");

    @Autowired PmGenerationService generation;
    @Autowired PmScheduleService scheduleService;
    @Autowired AssetService assets;
    @Autowired WorkOrderRepository workOrders;
    @Autowired WorkOrderService workOrderService;
    @Autowired WorkOrderNumberGenerator numbers;

    Plant p;

    @BeforeEach
    void setUp() {
        clock.set(T0);
        p = data.newPlant();
    }

    @Test
    void calendarTrigger_generatesOnceInTheLeadWindow_neverTwice_thenAgainAfterCompletion() {
        meter(p.machineId(), "1000");
        UUID schedule = schedule(p.machineId(), 30, "500", T0, "1000");

        at(Duration.ofDays(26));
        assertThat(ordersFor(schedule)).as("day 26: before the 3-day lead window").isEmpty();

        at(Duration.ofDays(27));
        assertThat(ordersFor(schedule)).as("day 27: lead window opens").hasSize(1);
        WorkOrder first = ordersFor(schedule).get(0);
        assertThat(first.getDueAt()).isEqualTo(T0.plus(Duration.ofDays(30)));
        assertThat(first.getPmSequence()).isEqualTo(1);

        generation.generateForAllTenants();
        generation.generateForAllTenants();
        assertThat(ordersFor(schedule)).as("re-running the job creates no duplicates").hasSize(1);

        at(Duration.ofDays(31));
        assertThat(ordersFor(schedule)).as("overdue order is not stacked with a second copy").hasSize(1);
        boolean overdue = data.asUser(p.manager(), () -> workOrderService.get(first.getId(), data.principal(p.manager())).overdue());
        assertThat(overdue).isTrue();

        complete(first.getId());   // done on day 31 → next due day 61, generated from day 58

        at(Duration.ofDays(31 + 26));
        assertThat(ordersFor(schedule)).hasSize(1);
        at(Duration.ofDays(31 + 27));
        List<WorkOrder> orders = ordersFor(schedule);
        assertThat(orders).hasSize(2);
        assertThat(orders.get(1).getPmSequence()).isEqualTo(2);
        assertThat(orders.get(1).getDueAt()).isEqualTo(T0.plus(Duration.ofDays(61)));
    }

    @Test
    void runningHoursTrigger_firesFirst_whenTheMachineRunsHard() {
        meter(p.machineId(), "2000");
        UUID schedule = schedule(p.machineId(), 90, "200", T0, "2000");

        clock.set(T0.plus(Duration.ofDays(5)));
        meter(p.machineId(), "2170");
        generation.generateForAllTenants();
        assertThat(ordersFor(schedule)).as("170 h of 200: below the 20 h lead").isEmpty();

        meter(p.machineId(), "2185");
        generation.generateForAllTenants();
        List<WorkOrder> orders = ordersFor(schedule);
        assertThat(orders).as("185 h of 200: generated, 85 days before the calendar would").hasSize(1);
        assertThat(orders.get(0).getDueRunningMinutes()).isEqualTo(2200L * 60);
        assertThat(orders.get(0).getDescription()).contains("Running hours (200 h)");
    }

    @Test
    void databaseRejectsADuplicateOccurrence_evenIfApplicationCodeIsBypassed() {
        UUID schedule = schedule(p.machineId(), 30, null, T0.minus(Duration.ofDays(40)), null);
        generation.generateForAllTenants();
        assertThat(ordersFor(schedule)).hasSize(1);

        assertThatThrownBy(() -> data.as(p.tenantId(), () -> workOrders.saveAndFlush(WorkOrder.fromPmSchedule(
                numbers.next(T0), assets.load(p.machineId()), Priority.LOW, "dup", null,
                schedule, 1, null, null, null, T0))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void at(Duration sinceT0) {
        clock.set(T0.plus(sinceT0));
        generation.generateForAllTenants();
    }

    private UUID schedule(UUID assetId, int days, String hours, Instant lastDoneAt, String lastDoneHours) {
        return data.asUser(p.manager(), () -> scheduleService.create(new PmScheduleService.CreateRequest(
                assetId, "Bearing greasing", null, Trade.MECHANICAL, Priority.MEDIUM, days,
                hours == null ? null : new BigDecimal(hours), lastDoneAt,
                lastDoneHours == null ? null : new BigDecimal(lastDoneHours)))).id();
    }

    private void meter(UUID assetId, String hours) {
        data.asUser(p.admin(), () -> assets.recordMeterReading(assetId, new MeterReadingRequest(new BigDecimal(hours))));
    }

    private List<WorkOrder> ordersFor(UUID scheduleId) {
        return data.as(p.tenantId(), () -> workOrders.findPmOrdersWithStatusIn(EnumSet.allOf(WorkOrderStatus.class)).stream()
                .filter(w -> scheduleId.equals(w.getPmScheduleId()))
                .sorted(Comparator.comparing(WorkOrder::getPmSequence))
                .toList());
    }

    private void complete(UUID workOrderId) {
        var mgr = data.principal(p.manager());
        data.asUser(p.manager(), () -> {
            workOrderService.assign(workOrderId, p.tech1().getId(), mgr);
            workOrderService.transition(workOrderId, new WorkOrderDtos.TransitionRequest(WorkOrderAction.START, null, null), mgr);
            workOrderService.logLabour(workOrderId, new WorkOrderDtos.LabourRequest(45, LocalDate.of(2026, 2, 5), null, p.tech1().getId()), mgr);
            return workOrderService.transition(workOrderId,
                    new WorkOrderDtos.TransitionRequest(WorkOrderAction.RETURN_TO_SERVICE, null, null), mgr);
        });
    }
}
