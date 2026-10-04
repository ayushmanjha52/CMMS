package com.plantdesk.tenancy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import org.hibernate.Session;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.PreparedStatement;

/**
 * Applies tenant isolation at the start of every transaction — both layers, in one place.
 *
 * <ol>
 *   <li><b>Hibernate filter</b> — enabled on the Session, so every HQL/Criteria query gets
 *       {@code tenant_id = :tenantId} appended.</li>
 *   <li><b>Postgres session variables</b> — {@code app.tenant_id} (and user/role for the
 *       technician rule) set with {@code set_config(..., is_local => true)}, so the RLS
 *       policies see them. "Local" means they vanish at COMMIT/ROLLBACK, which is what makes
 *       this safe with a connection pool: a pooled connection can never carry plant A's
 *       tenant into plant B's request.</li>
 * </ol>
 *
 * <p>Why here and not in the servlet filter: with open-in-view disabled there is no Session
 * yet when the request filter runs. The request filter resolves the tenant from the JWT into
 * {@link TenantContext}; this class turns that into database state the moment a Session and
 * a connection exist. It also covers work that never goes through HTTP (the PM scheduler).
 *
 * <p>No tenant in context ⇒ nothing is set ⇒ RLS sees NULL ⇒ zero rows. Fails closed.
 */
public class TenantAwareJpaTransactionManager extends JpaTransactionManager {

    private static final String SET_GUCS =
            "select set_config('app.tenant_id', ?, true), "
                    + "set_config('app.user_id', ?, true), "
                    + "set_config('app.role', ?, true)";

    public TenantAwareJpaTransactionManager(EntityManagerFactory emf) {
        super(emf);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);
        TenantScope scope = TenantContext.current().orElse(null);
        if (scope == null) {
            return;
        }
        EntityManagerHolder holder =
                (EntityManagerHolder) TransactionSynchronizationManager.getResource(obtainEntityManagerFactory());
        EntityManager em = holder.getEntityManager();
        try {
            Session session = em.unwrap(Session.class);
            session.enableFilter(TenantFilters.TENANT)
                    .setParameter(TenantFilters.TENANT_PARAM, scope.tenantId());
            if (scope.restrictedToOwnWorkOrders()) {
                session.enableFilter(TenantFilters.ASSIGNEE)
                        .setParameter(TenantFilters.ASSIGNEE_PARAM, scope.userId());
            }
            session.doWork(connection -> {
                try (PreparedStatement ps = connection.prepareStatement(SET_GUCS)) {
                    ps.setString(1, scope.tenantId().toString());
                    ps.setString(2, scope.userId() == null ? "" : scope.userId().toString());
                    ps.setString(3, scope.roleNameForDatabase());
                    ps.execute();
                }
            });
        } catch (RuntimeException ex) {
            // super.doBegin has already bound the EntityManager to the thread. If we fail
            // after that, Spring will not clean up for us — roll back and unbind, or the next
            // request on this thread inherits a half-open transaction.
            EntityTransaction tx = em.getTransaction();
            if (tx.isActive()) {
                tx.rollback();
            }
            doCleanupAfterCompletion(transaction);
            throw ex;
        }
    }
}
