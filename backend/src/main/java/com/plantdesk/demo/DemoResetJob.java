package com.plantdesk.demo;

import com.plantdesk.tenancy.SystemLookupDao;
import com.plantdesk.tenancy.TenantContext;
import com.plantdesk.tenancy.TenantScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Wipes and reseeds the public demo plants every night (03:00 IST). Anyone can log in as the
 * demo admin, so whatever a visitor types stays for at most a day; reseeding also keeps the
 * "last 12 months" of breakdown history relative to today.
 *
 * <p>The wipe runs under each demo tenant's own scope, so row-level security limits it to
 * that tenant's rows: even a bug in this list cannot touch a real plant.
 */
@Component
@ConditionalOnProperty(name = "plantdesk.demo.enabled", havingValue = "true")
public class DemoResetJob {

    private static final Logger log = LoggerFactory.getLogger(DemoResetJob.class);

    // Children before parents, so every foreign key is satisfied at each step.
    private static final List<String> WIPE_ORDER = List.of(
            "part_consumptions", "labour_entries", "work_orders", "pm_schedules", "meter_readings",
            "asset_code_counters", "work_order_counters", "assets", "spare_parts", "refresh_tokens", "users");

    private final SystemLookupDao lookup;
    private final TransactionTemplate tx;
    private final JdbcTemplate jdbc;
    private final DemoDataSeeder seeder;

    public DemoResetJob(SystemLookupDao lookup, TransactionTemplate tx, JdbcTemplate jdbc, DemoDataSeeder seeder) {
        this.lookup = lookup;
        this.tx = tx;
        this.jdbc = jdbc;
        this.seeder = seeder;
    }

    @Scheduled(cron = "${plantdesk.demo.reset-cron:0 30 21 * * *}", zone = "UTC")
    public void reset() {
        for (String code : List.of("DEMO", "LOCO")) {
            lookup.findTenantIdByCode(code).ifPresent(id ->
                    TenantContext.runAs(TenantScope.system(id), () -> tx.executeWithoutResult(s -> wipe(id))));
        }
        seeder.seedIfMissing();
        log.info("Demo plants reset and reseeded");
    }

    private void wipe(UUID tenantId) {
        for (String table : WIPE_ORDER) {
            jdbc.update("delete from " + table + " where tenant_id = ?", tenantId);
        }
        jdbc.update("delete from tenants where id = ?", tenantId);
    }
}
