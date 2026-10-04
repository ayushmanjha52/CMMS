package com.plantdesk.pm;

import com.plantdesk.security.Roles;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/pm-schedules")
public class PmScheduleController {

    private final PmScheduleService service;
    private final PmGenerationService generation;

    public PmScheduleController(PmScheduleService service, PmGenerationService generation) {
        this.service = service;
        this.generation = generation;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PLANT_ADMIN','MAINTENANCE_MANAGER','VIEWER')")
    public List<PmScheduleService.ScheduleView> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.MANAGE_WORK)
    public PmScheduleService.ScheduleView create(@Valid @RequestBody PmScheduleService.CreateRequest request) {
        return service.create(request);
    }

    @PostMapping("/{id}/active")
    @PreAuthorize(Roles.MANAGE_WORK)
    public PmScheduleService.ScheduleView setActive(@PathVariable UUID id, @RequestParam("value") boolean value) {
        return service.setActive(id, value);
    }

    /** Run the generator now for this plant only, instead of waiting for the next scheduled run. */
    @PostMapping("/run")
    @PreAuthorize(Roles.MANAGE_WORK)
    @Transactional
    public Map<String, Integer> runNow() {
        return Map.of("generated", generation.generateDue().size());
    }
}
