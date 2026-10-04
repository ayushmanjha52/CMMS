package com.plantdesk.analytics;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Plant-wide numbers. Technicians are excluded on purpose: their session only sees their own
 * work orders, so an MTBF computed for them would be wrong, not just partial.
 */
@RestController
@RequestMapping("/api/analytics")
@PreAuthorize("hasAnyRole('PLANT_ADMIN','MAINTENANCE_MANAGER','VIEWER')")
public class AnalyticsController {

    private final ReliabilityService service;

    public AnalyticsController(ReliabilityService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public ReliabilityService.Summary summary() {
        return service.summary();
    }

    @GetMapping("/assets/{id}/reliability")
    public ReliabilityService.AssetReliability reliability(@PathVariable UUID id,
                                                           @RequestParam(name = "months", defaultValue = "12") int months) {
        return service.reliability(id, months);
    }

    @GetMapping("/assets/{id}/health-strip")
    public ReliabilityService.HealthStrip healthStrip(@PathVariable UUID id) {
        return service.healthStrip(id);
    }

    @GetMapping("/degrading")
    public List<ReliabilityService.DegradingAsset> degrading() {
        return service.degrading();
    }

    @GetMapping("/downtime")
    public ReliabilityService.DowntimeReport downtime(@RequestParam(name = "months", defaultValue = "3") int months) {
        return service.downtime(months);
    }
}
