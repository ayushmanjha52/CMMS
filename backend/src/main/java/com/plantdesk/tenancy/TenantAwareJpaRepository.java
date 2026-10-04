package com.plantdesk.tenancy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;

import java.util.Optional;

/**
 * Base class for every Spring Data repository in the app.
 *
 * <p>The trap this closes: Hibernate filters apply to <em>queries</em>, but
 * {@code SimpleJpaRepository.findById} calls {@code EntityManager.find}, which loads by
 * primary key and skips filters entirely. Left alone, "Tenant A fetches Tenant B's work
 * order by id" would be stopped only by RLS — one layer, not two. So by-id lookups are
 * routed through a JPQL query, which the filter does apply to.
 *
 * <p>{@code getReferenceById} is overridden for the same reason: it returns a lazy proxy
 * whose initialisation is a by-key load.
 */
public class TenantAwareJpaRepository<T, ID> extends SimpleJpaRepository<T, ID> {

    private final JpaEntityInformation<T, ?> entityInformation;
    private final EntityManager em;

    public TenantAwareJpaRepository(JpaEntityInformation<T, ?> entityInformation, EntityManager em) {
        super(entityInformation, em);
        this.entityInformation = entityInformation;
        this.em = em;
    }

    @Override
    public Optional<T> findById(ID id) {
        String idAttribute = entityInformation.getRequiredIdAttribute().getName();
        String jpql = "select e from " + entityInformation.getEntityName() + " e where e." + idAttribute + " = :id";
        return em.createQuery(jpql, getDomainClass())
                .setParameter("id", id)
                .getResultStream()
                .findFirst();
    }

    @Override
    public T getReferenceById(ID id) {
        return findById(id).orElseThrow(() -> new EntityNotFoundException(
                entityInformation.getEntityName() + " " + id + " not found"));
    }

    @Override
    @Deprecated
    public T getById(ID id) {
        return getReferenceById(id);
    }

    @Override
    @Deprecated
    public T getOne(ID id) {
        return getReferenceById(id);
    }
}
