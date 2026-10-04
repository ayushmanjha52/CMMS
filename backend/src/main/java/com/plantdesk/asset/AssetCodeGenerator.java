package com.plantdesk.asset;

import com.plantdesk.tenancy.TenantContext;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Generates the last segment of an asset tag: PLT-01, AREA-03, LN-02, MTR-114.
 *
 * <p>Numbering is per prefix across the whole plant, not per parent: motor numbers in a
 * plant are unique plant-wide (MTR-114 is one motor wherever it sits), which is how
 * equipment is stencilled and how people refer to it on the radio.
 */
@Component
public class AssetCodeGenerator {

    private static final Pattern TYPE_PREFIX = Pattern.compile("[A-Z]{2,5}");

    private final EntityManager em;

    public AssetCodeGenerator(EntityManager em) {
        this.em = em;
    }

    public String next(AssetLevel level, String typePrefix) {
        String prefix;
        if (level.needsTypePrefix()) {
            if (typePrefix == null || !TYPE_PREFIX.matcher(typePrefix.toUpperCase()).matches()) {
                throw new IllegalArgumentException(level + " needs an equipment-type prefix of 2–5 letters, e.g. MTR, PMP, GBX");
            }
            prefix = typePrefix.toUpperCase();
        } else {
            prefix = level.fixedPrefix();
        }
        // Atomic increment-or-create; the row lock taken by ON CONFLICT DO UPDATE serialises
        // concurrent callers for the same prefix. Native SQL, so tenant_id is passed
        // explicitly — and RLS WITH CHECK rejects it if it does not match the session.
        Number value = (Number) em.createNativeQuery("""
                        insert into asset_code_counters (tenant_id, prefix, last_value) values (:tenant, :prefix, 1)
                        on conflict (tenant_id, prefix) do update set last_value = asset_code_counters.last_value + 1
                        returning last_value""")
                .setParameter("tenant", TenantContext.require().tenantId())
                .setParameter("prefix", prefix)
                .getSingleResult();
        return prefix + "-" + String.format("%0" + level.digits() + "d", value.intValue());
    }
}
