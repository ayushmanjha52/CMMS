package com.plantdesk.pm;

import com.plantdesk.asset.Asset;
import com.plantdesk.tenancy.TenantScopedEntity;
import com.plantdesk.user.Trade;
import com.plantdesk.workorder.Priority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;

/**
 * A preventive maintenance plan with a dual trigger: every N days, or every M running
 * hours since it was last done — whichever comes first.
 *
 * <p>The baseline (last done at / at what meter reading) moves when the PM work order is
 * returned to service, not when it was due. Doing a 30-day PM ten days late means the next
 * one is due 30 days after it was actually done, which is how planners count it.
 */
@Entity
@Table(name = "pm_schedules")
public class PmSchedule extends TenantScopedEntity {

    /** Generate this fraction of the interval ahead of due, so the planner can book shutdown and spares. */
    static final double LEAD_FRACTION = 0.10;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false, updatable = false)
    private Asset asset;

    @Column(nullable = false)
    private String title;

    private String instructions;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Trade trade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority;

    @Column(name = "interval_days")
    private Integer intervalDays;

    @Column(name = "interval_running_minutes")
    private Long intervalRunningMinutes;

    @Column(name = "last_done_at", nullable = false)
    private Instant lastDoneAt;

    @Column(name = "last_done_running_minutes", nullable = false)
    private long lastDoneRunningMinutes;

    @Column(name = "generated_count", nullable = false)
    private int generatedCount;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PmSchedule() {}

    public PmSchedule(Asset asset, String title, String instructions, Trade trade, Priority priority,
                      Integer intervalDays, Long intervalRunningMinutes, Instant baselineAt,
                      long baselineRunningMinutes, Instant now) {
        if (intervalDays == null && intervalRunningMinutes == null) {
            throw new IllegalArgumentException("A PM schedule needs a calendar interval, a running-hours interval, or both");
        }
        if ((intervalDays != null && intervalDays <= 0) || (intervalRunningMinutes != null && intervalRunningMinutes <= 0)) {
            throw new IllegalArgumentException("Intervals must be positive");
        }
        this.asset = asset;
        this.title = title;
        this.instructions = instructions;
        this.trade = trade;
        this.priority = priority;
        this.intervalDays = intervalDays;
        this.intervalRunningMinutes = intervalRunningMinutes;
        this.lastDoneAt = baselineAt;
        this.lastDoneRunningMinutes = baselineRunningMinutes;
        this.createdAt = now;
    }

    public enum Trigger { CALENDAR, RUNNING_HOURS }

    /** What the schedule says right now. */
    public record Evaluation(boolean shouldGenerate, Trigger trigger, Instant dueAt, Long dueRunningMinutes,
                             Instant generateFrom, Long runningMinutesRemaining) {}

    public Evaluation evaluate(Instant now, long currentRunningMinutes) {
        Instant dueAt = null;
        Instant calendarGenerateFrom = null;
        if (intervalDays != null) {
            Duration interval = Duration.ofDays(intervalDays);
            dueAt = lastDoneAt.plus(interval);
            calendarGenerateFrom = dueAt.minus(leadOf(interval));
        }
        Long dueRunning = null;
        Long hoursGenerateFrom = null;
        if (intervalRunningMinutes != null) {
            dueRunning = lastDoneRunningMinutes + intervalRunningMinutes;
            hoursGenerateFrom = dueRunning - (long) Math.floor(intervalRunningMinutes * LEAD_FRACTION);
        }
        boolean calendarHit = calendarGenerateFrom != null && !now.isBefore(calendarGenerateFrom);
        boolean hoursHit = hoursGenerateFrom != null && currentRunningMinutes >= hoursGenerateFrom;
        Trigger trigger = calendarHit ? Trigger.CALENDAR : hoursHit ? Trigger.RUNNING_HOURS : null;
        Long remaining = dueRunning == null ? null : dueRunning - currentRunningMinutes;
        return new Evaluation(active && (calendarHit || hoursHit), trigger, dueAt, dueRunning,
                calendarGenerateFrom, remaining);
    }

    /** Lead time: 10% of the interval, at least one day. A weekly PM appears one day early. */
    static Duration leadOf(Duration interval) {
        Duration lead = Duration.ofSeconds((long) (interval.toSeconds() * LEAD_FRACTION));
        return lead.compareTo(Duration.ofDays(1)) < 0 ? Duration.ofDays(1) : lead;
    }

    /** Returns the occurrence number for the order being generated. */
    int nextOccurrence() {
        generatedCount += 1;
        return generatedCount;
    }

    void markDone(Instant completedAt, long runningMinutesAtCompletion) {
        this.lastDoneAt = completedAt;
        this.lastDoneRunningMinutes = runningMinutesAtCompletion;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Asset getAsset() { return asset; }
    public String getTitle() { return title; }
    public String getInstructions() { return instructions; }
    public Trade getTrade() { return trade; }
    public Priority getPriority() { return priority; }
    public Integer getIntervalDays() { return intervalDays; }
    public Long getIntervalRunningMinutes() { return intervalRunningMinutes; }
    public Instant getLastDoneAt() { return lastDoneAt; }
    public long getLastDoneRunningMinutes() { return lastDoneRunningMinutes; }
    public int getGeneratedCount() { return generatedCount; }
    public boolean isActive() { return active; }
}
