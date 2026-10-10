package com.plantdesk.security;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Counts hits per key in fixed time windows. In-memory, so limits are per instance: fine
 * for one container; with several replicas this moves to Redis (INCR + EXPIRE).
 */
final class FixedWindowCounter {

    private record Window(long startMillis, int count) {}

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int limit;
    private final long windowMillis;
    private final Clock clock;

    FixedWindowCounter(int limit, Duration window, Clock clock) {
        this.limit = limit;
        this.windowMillis = window.toMillis();
        this.clock = clock;
    }

    /** Counts one hit. Returns seconds until the window resets if this hit is over the limit, else 0. */
    long hit(String key) {
        long now = clock.millis();
        Window w = windows.compute(key, (k, old) ->
                old == null || now - old.startMillis >= windowMillis ? new Window(now, 1) : new Window(old.startMillis, old.count + 1));
        return w.count > limit ? secondsLeft(w, now) : 0;
    }

    /** Seconds the key must wait because it has already used its whole allowance, else 0. Does not count. */
    long blockedFor(String key) {
        long now = clock.millis();
        Window w = windows.get(key);
        if (w == null || now - w.startMillis >= windowMillis || w.count < limit) {
            return 0;
        }
        return secondsLeft(w, now);
    }

    void reset(String key) {
        windows.remove(key);
    }

    void evictExpired() {
        long now = clock.millis();
        windows.entrySet().removeIf(e -> now - e.getValue().startMillis >= windowMillis);
    }

    private long secondsLeft(Window w, long now) {
        return Math.max(1, (w.startMillis + windowMillis - now + 999) / 1000);
    }
}
