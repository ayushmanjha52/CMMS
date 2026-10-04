package com.plantdesk.workorder;

import com.plantdesk.tenancy.TenantContext;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;

/**
 * WO-2026-00042: year-prefixed, gap-tolerant, per plant. Numbering restarts each year
 * because that is how job card registers are kept and audited.
 */
@Component
public class WorkOrderNumberGenerator {

    private final EntityManager em;

    public WorkOrderNumberGenerator(EntityManager em) {
        this.em = em;
    }

    public String next(Instant now) {
        int year = now.atZone(ZoneOffset.UTC).getYear();
        Number value = (Number) em.createNativeQuery("""
                        insert into work_order_counters (tenant_id, year, last_value) values (:tenant, :year, 1)
                        on conflict (tenant_id, year) do update set last_value = work_order_counters.last_value + 1
                        returning last_value""")
                .setParameter("tenant", TenantContext.require().tenantId())
                .setParameter("year", year)
                .getSingleResult();
        return String.format("WO-%d-%05d", year, value.intValue());
    }
}
