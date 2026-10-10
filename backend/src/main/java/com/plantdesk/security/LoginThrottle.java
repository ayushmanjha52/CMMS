package com.plantdesk.security;

import com.plantdesk.config.PlantDeskProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Brute-force protection for sign-in, in two independent dimensions:
 *
 * <ul>
 *   <li><b>Per IP</b> — every login attempt counts. Stops one machine hammering many accounts.</li>
 *   <li><b>Per account</b> (plant code + email) — only failures count, and a success clears
 *       them. Stops a botnet guessing one person's password from thousands of IPs, which a
 *       per-IP limit alone cannot see.</li>
 * </ul>
 * The per-account limit is a temporary throttle, not a permanent lockout: a lockout would let
 * anyone who knows an engineer's email keep them out of the system indefinitely.
 */
@Component
public class LoginThrottle {

    private final FixedWindowCounter perIp;
    private final FixedWindowCounter accountFailures;

    public LoginThrottle(PlantDeskProperties props, Clock clock) {
        var rl = props.rateLimit();
        this.perIp = new FixedWindowCounter(rl.loginPerIp(), rl.ipWindow(), clock);
        this.accountFailures = new FixedWindowCounter(rl.accountFailures(), rl.accountWindow(), clock);
    }

    /** Counts a login attempt from this IP; returns seconds to wait if over the limit, else 0. */
    public long hitFromIp(String ip) {
        return perIp.hit(ip);
    }

    public long accountBlockedFor(String plantCode, String email) {
        return accountFailures.blockedFor(accountKey(plantCode, email));
    }

    public void recordFailure(String plantCode, String email) {
        accountFailures.hit(accountKey(plantCode, email));
    }

    public void recordSuccess(String plantCode, String email) {
        accountFailures.reset(accountKey(plantCode, email));
    }

    @Scheduled(fixedDelay = 600_000)
    void evictExpired() {
        perIp.evictExpired();
        accountFailures.evictExpired();
    }

    private static String accountKey(String plantCode, String email) {
        return plantCode.trim().toUpperCase() + "|" + email.trim().toLowerCase();
    }
}
