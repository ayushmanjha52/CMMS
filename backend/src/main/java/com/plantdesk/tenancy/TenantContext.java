package com.plantdesk.tenancy;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Thread-bound tenant for the current unit of work.
 *
 * <p>Set by {@code JwtAuthenticationFilter} for HTTP requests and by {@link #callAs} for
 * background work. Read by {@link TenantAwareJpaTransactionManager} when a transaction
 * begins, which is the moment the Hibernate filter and the Postgres session variable are
 * applied. Consequence: the tenant must be set <em>before</em> the transaction starts and
 * cannot change inside one — {@link #callAs} enforces that.
 */
public final class TenantContext {

    private static final ThreadLocal<TenantScope> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static Optional<TenantScope> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static TenantScope require() {
        TenantScope scope = CURRENT.get();
        if (scope == null) {
            throw new IllegalStateException("No tenant bound to this thread");
        }
        return scope;
    }

    public static void set(TenantScope scope) {
        CURRENT.set(scope);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static <T> T callAs(TenantScope scope, Supplier<T> work) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            // The open transaction already has its tenant filter and RLS variable applied;
            // switching the ThreadLocal now would make the two disagree.
            throw new IllegalStateException("Cannot switch tenant inside an active transaction");
        }
        TenantScope previous = CURRENT.get();
        CURRENT.set(scope);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static void runAs(TenantScope scope, Runnable work) {
        callAs(scope, () -> {
            work.run();
            return null;
        });
    }
}
