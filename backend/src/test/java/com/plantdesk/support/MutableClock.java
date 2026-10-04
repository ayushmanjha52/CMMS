package com.plantdesk.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Real time by default; tests can freeze it and move it forward. */
public class MutableClock extends Clock {

    private volatile Instant frozen;

    public void set(Instant instant) {
        this.frozen = instant;
    }

    public void advance(Duration duration) {
        this.frozen = instant().plus(duration);
    }

    public void reset() {
        this.frozen = null;
    }

    @Override
    public Instant instant() {
        Instant f = frozen;
        return f != null ? f : Instant.now();
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }
}
