package com.plantdesk.workorder;

/**
 * <pre>
 *            START              RETURN_TO_SERVICE          CLOSE
 *   OPEN ───────────► IN_PROGRESS ─────────────► COMPLETED ──────► CLOSED
 *    │                 │   ▲                       │
 *    │            HOLD │   │ START                 │ REWORK (manager rejects closure)
 *    │                 ▼   │                       ▼
 *    │               ON_HOLD                   IN_PROGRESS
 *    │                 │
 *    └──── CANCEL ─────┴──► CANCELLED
 * </pre>
 * COMPLETED and CLOSED are separate because plants separate them: the technician returns
 * the equipment to service, the maintenance engineer reviews the job card and closes it.
 */
public enum WorkOrderStatus {
    OPEN,
    IN_PROGRESS,
    ON_HOLD,
    COMPLETED,
    CLOSED,
    CANCELLED;

    /** Work still outstanding — what an overdue check or "open jobs" count cares about. */
    public boolean isOutstanding() {
        return this == OPEN || this == IN_PROGRESS || this == ON_HOLD;
    }
}
