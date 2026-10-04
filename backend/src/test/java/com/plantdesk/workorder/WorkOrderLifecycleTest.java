package com.plantdesk.workorder;

import com.plantdesk.asset.Asset;
import com.plantdesk.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** The state machine, with no Spring and no database. */
class WorkOrderLifecycleTest {

    private static final Instant T = Instant.parse("2026-03-01T08:00:00Z");

    private final Asset asset = mock(Asset.class);
    private final User technician = mock(User.class);

    private WorkOrder breakdown() {
        return WorkOrder.breakdown("WO-2026-00001", asset, Priority.HIGH, "Motor tripped", null, null, T, UUID.randomUUID(), T);
    }

    private static WorkOrder.TransitionInput at(long labourMinutes, String note, FailureCode code) {
        return new WorkOrder.TransitionInput(T.plusSeconds(3600), labourMinutes, note, code);
    }

    /** Drives a fresh order into the given status along the legal path. */
    private WorkOrder in(WorkOrderStatus target) {
        WorkOrder wo = breakdown();
        wo.assign(technician);
        switch (target) {
            case OPEN -> {}
            case IN_PROGRESS -> wo.apply(WorkOrderAction.START, at(0, null, null));
            case ON_HOLD -> {
                wo.apply(WorkOrderAction.START, at(0, null, null));
                wo.apply(WorkOrderAction.HOLD, at(0, "awaiting spares", null));
            }
            case COMPLETED -> {
                wo.apply(WorkOrderAction.START, at(0, null, null));
                wo.apply(WorkOrderAction.RETURN_TO_SERVICE, at(60, null, FailureCode.M01));
            }
            case CLOSED -> {
                wo.apply(WorkOrderAction.START, at(0, null, null));
                wo.apply(WorkOrderAction.RETURN_TO_SERVICE, at(60, null, FailureCode.M01));
                wo.apply(WorkOrderAction.CLOSE, at(60, null, null));
            }
            case CANCELLED -> wo.apply(WorkOrderAction.CANCEL, at(0, "raised in error", null));
        }
        assertThat(wo.getStatus()).isEqualTo(target);
        return wo;
    }

    @Test
    void openCannotJumpStraightToClosed() {
        WorkOrder wo = in(WorkOrderStatus.OPEN);
        assertThatThrownBy(() -> wo.apply(WorkOrderAction.CLOSE, at(60, null, null)))
                .isInstanceOf(InvalidTransitionException.class)
                .hasMessage("Cannot CLOSE work order WO-2026-00001: it is OPEN. CLOSE is only allowed from [COMPLETED].");
        assertThat(wo.getStatus()).isEqualTo(WorkOrderStatus.OPEN);
    }

    @Test
    void cannotReturnToServiceWithoutLabour() {
        WorkOrder wo = in(WorkOrderStatus.IN_PROGRESS);
        assertThatThrownBy(() -> wo.apply(WorkOrderAction.RETURN_TO_SERVICE, at(0, null, FailureCode.M01)))
                .isInstanceOf(InvalidTransitionException.class)
                .hasMessageContaining("Record labour hours");
    }

    @Test
    void breakdownNeedsAFailureCodeToReturnToService() {
        WorkOrder wo = in(WorkOrderStatus.IN_PROGRESS);
        assertThatThrownBy(() -> wo.apply(WorkOrderAction.RETURN_TO_SERVICE, at(60, null, null)))
                .hasMessageContaining("failure code");
    }

    @Test
    void cannotStartWithoutAnAssignee() {
        WorkOrder wo = breakdown();
        assertThatThrownBy(() -> wo.apply(WorkOrderAction.START, at(0, null, null)))
                .hasMessageContaining("Assign a technician");
    }

    @Test
    void holdAndCancelNeedAReason() {
        assertThatThrownBy(() -> in(WorkOrderStatus.IN_PROGRESS).apply(WorkOrderAction.HOLD, at(0, " ", null)))
                .hasMessageContaining("hold reason");
        assertThatThrownBy(() -> in(WorkOrderStatus.OPEN).apply(WorkOrderAction.CANCEL, at(0, null, null)))
                .hasMessageContaining("reason for cancelling");
    }

    @Test
    void returnToServiceClosesTheDowntimeWindow() {
        WorkOrder wo = in(WorkOrderStatus.COMPLETED);
        assertThat(wo.getDowntimeStart()).isEqualTo(T);
        assertThat(wo.getDowntimeEnd()).isEqualTo(T.plusSeconds(3600));
    }

    @Test
    void reworkSendsACompletedJobBackInProgress() {
        WorkOrder wo = in(WorkOrderStatus.COMPLETED);
        wo.apply(WorkOrderAction.REWORK, at(60, "IR value still low, re-dry the winding", null));
        assertThat(wo.getStatus()).isEqualTo(WorkOrderStatus.IN_PROGRESS);
        assertThat(wo.getCompletedAt()).isNull();
    }

    /** Exhaustive: every action from every status either follows the table or is rejected. */
    @ParameterizedTest
    @EnumSource(WorkOrderStatus.class)
    void everyActionFromEveryStatusMatchesTheTransitionTable(WorkOrderStatus from) {
        for (WorkOrderAction action : WorkOrderAction.values()) {
            WorkOrder wo = in(from);
            WorkOrder.TransitionInput input = at(60, "reason", FailureCode.M01);
            if (action.allowedFrom().contains(from)) {
                wo.apply(action, input);
                assertThat(wo.getStatus()).as("%s from %s", action, from).isEqualTo(action.target());
            } else {
                assertThatThrownBy(() -> wo.apply(action, input)).as("%s from %s", action, from)
                        .isInstanceOf(InvalidTransitionException.class);
                assertThat(wo.getStatus()).isEqualTo(from);
            }
        }
    }

    @Test
    void terminalStatesAllowNothing() {
        assertThat(in(WorkOrderStatus.CLOSED).availableActions()).isEmpty();
        assertThat(in(WorkOrderStatus.CANCELLED).availableActions()).isEmpty();
        assertThat(in(WorkOrderStatus.OPEN).availableActions())
                .isEqualTo(EnumSet.of(WorkOrderAction.START, WorkOrderAction.CANCEL));
    }
}
