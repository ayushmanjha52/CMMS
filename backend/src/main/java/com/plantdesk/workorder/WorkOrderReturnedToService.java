package com.plantdesk.workorder;

import java.time.Instant;
import java.util.UUID;

/**
 * Published inside the transition's transaction. The PM module listens so that a completed
 * PM resets its schedule's baseline — without the work order module depending on PM.
 */
public record WorkOrderReturnedToService(UUID workOrderId, UUID pmScheduleId, Instant completedAt,
                                         long runningMinutesAtCompletion) {}
