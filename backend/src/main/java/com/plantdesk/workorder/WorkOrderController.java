package com.plantdesk.workorder;

import com.plantdesk.security.AuthenticatedUser;
import com.plantdesk.security.Roles;
import com.plantdesk.workorder.WorkOrderDtos.Detail;
import com.plantdesk.workorder.WorkOrderDtos.FailureCodeView;
import com.plantdesk.workorder.WorkOrderDtos.ListItem;
import com.plantdesk.workorder.WorkOrderDtos.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/work-orders")
public class WorkOrderController {

    private final WorkOrderService service;

    public WorkOrderController(WorkOrderService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(Roles.ANY)
    public PageResponse<ListItem> list(@RequestParam(name = "status", required = false) Set<WorkOrderStatus> status,
                                       @RequestParam(name = "type", required = false) WorkOrderType type,
                                       @RequestParam(name = "priority", required = false) Priority priority,
                                       @RequestParam(name = "assetId", required = false) UUID assetId,
                                       @RequestParam(name = "page", defaultValue = "0") int page,
                                       @RequestParam(name = "size", defaultValue = "50") int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200),
                Sort.by(Sort.Order.desc("raisedAt"), Sort.Order.desc("number")));
        return PageResponse.of(service.list(new WorkOrderService.ListFilter(status, type, priority, assetId), pageable));
    }

    @GetMapping("/failure-codes")
    @PreAuthorize(Roles.ANY)
    public List<FailureCodeView> failureCodes() {
        return Arrays.stream(FailureCode.values()).map(WorkOrderDtos::failureCode).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize(Roles.ANY)
    public Detail get(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser me) {
        return service.get(id, me);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.DO_WORK)
    public Detail raise(@Valid @RequestBody WorkOrderDtos.RaiseRequest request,
                        @AuthenticationPrincipal AuthenticatedUser me) {
        return service.raise(request, me);
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize(Roles.MANAGE_WORK)
    public Detail assign(@PathVariable UUID id, @Valid @RequestBody WorkOrderDtos.AssignRequest request,
                         @AuthenticationPrincipal AuthenticatedUser me) {
        return service.assign(id, request.technicianId(), me);
    }

    @PostMapping("/{id}/transitions")
    @PreAuthorize(Roles.DO_WORK)
    public Detail transition(@PathVariable UUID id, @Valid @RequestBody WorkOrderDtos.TransitionRequest request,
                             @AuthenticationPrincipal AuthenticatedUser me) {
        return service.transition(id, request, me);
    }

    @PostMapping("/{id}/labour")
    @PreAuthorize(Roles.DO_WORK)
    public Detail logLabour(@PathVariable UUID id, @Valid @RequestBody WorkOrderDtos.LabourRequest request,
                            @AuthenticationPrincipal AuthenticatedUser me) {
        return service.logLabour(id, request, me);
    }

    @PostMapping("/{id}/parts")
    @PreAuthorize(Roles.DO_WORK)
    public Detail consumePart(@PathVariable UUID id, @Valid @RequestBody WorkOrderDtos.PartRequest request,
                              @AuthenticationPrincipal AuthenticatedUser me) {
        return service.consumePart(id, request, me);
    }

    @PutMapping("/{id}/downtime")
    @PreAuthorize(Roles.MANAGE_WORK)
    public Detail correctDowntime(@PathVariable UUID id, @Valid @RequestBody WorkOrderDtos.DowntimeRequest request,
                                  @AuthenticationPrincipal AuthenticatedUser me) {
        return service.correctDowntime(id, request, me);
    }
}
