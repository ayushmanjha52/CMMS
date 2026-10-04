package com.plantdesk.workorder;

import java.util.EnumSet;
import java.util.Set;

import static com.plantdesk.workorder.WorkOrderStatus.CANCELLED;
import static com.plantdesk.workorder.WorkOrderStatus.CLOSED;
import static com.plantdesk.workorder.WorkOrderStatus.COMPLETED;
import static com.plantdesk.workorder.WorkOrderStatus.IN_PROGRESS;
import static com.plantdesk.workorder.WorkOrderStatus.ON_HOLD;
import static com.plantdesk.workorder.WorkOrderStatus.OPEN;

/**
 * The only way a status changes. Clients send an action, never a target status, so there
 * is no request that can say "set status to CLOSED" — only "CLOSE", which is checked
 * against where the order is now.
 */
public enum WorkOrderAction {
    START(EnumSet.of(OPEN, ON_HOLD), IN_PROGRESS, false),
    HOLD(EnumSet.of(IN_PROGRESS), ON_HOLD, false),
    RETURN_TO_SERVICE(EnumSet.of(IN_PROGRESS), COMPLETED, false),
    CLOSE(EnumSet.of(COMPLETED), CLOSED, true),
    REWORK(EnumSet.of(COMPLETED), IN_PROGRESS, true),
    CANCEL(EnumSet.of(OPEN, ON_HOLD), CANCELLED, true);

    private final Set<WorkOrderStatus> allowedFrom;
    private final WorkOrderStatus target;
    private final boolean managerOnly;

    WorkOrderAction(Set<WorkOrderStatus> allowedFrom, WorkOrderStatus target, boolean managerOnly) {
        this.allowedFrom = allowedFrom;
        this.target = target;
        this.managerOnly = managerOnly;
    }

    public Set<WorkOrderStatus> allowedFrom() {
        return allowedFrom;
    }

    public WorkOrderStatus target() {
        return target;
    }

    /** Closure approval, rework and cancellation are supervisory decisions. */
    public boolean managerOnly() {
        return managerOnly;
    }
}
