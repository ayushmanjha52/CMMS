package com.plantdesk.security;

/** 429, carrying how long the client should wait (sent as Retry-After). */
public class TooManyRequestsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(long retryAfterSeconds) {
        super(message(retryAfterSeconds));
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    static String message(long seconds) {
        long minutes = Math.max(1, (seconds + 59) / 60);
        return "Too many sign-in attempts. Try again in about " + minutes + (minutes == 1 ? " minute." : " minutes.");
    }
}
