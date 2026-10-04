package com.plantdesk.pm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "plantdesk.pm.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class PmScheduler {

    private static final Logger log = LoggerFactory.getLogger(PmScheduler.class);

    private final PmGenerationService generation;

    public PmScheduler(PmGenerationService generation) {
        this.generation = generation;
    }

    @Scheduled(cron = "${plantdesk.pm.cron}", zone = "UTC")
    public void run() {
        var report = generation.generateForAllTenants();
        if (report.generated() > 0 || report.failedTenants() > 0) {
            log.info("PM run: {} tenants, {} work orders generated, {} tenants failed",
                    report.tenants(), report.generated(), report.failedTenants());
        }
    }
}
