package com.plantdesk.asset;

import com.plantdesk.asset.AssetDtos.AssetDetail;
import com.plantdesk.asset.AssetDtos.AssetRef;
import com.plantdesk.asset.AssetDtos.TreeNode;
import com.plantdesk.security.Roles;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/assets")
public class AssetController {

    private final AssetService service;

    public AssetController(AssetService service) {
        this.service = service;
    }

    @GetMapping("/tree")
    @PreAuthorize(Roles.ANY)
    public List<TreeNode> tree() {
        return service.tree();
    }

    @GetMapping
    @PreAuthorize(Roles.ANY)
    public List<AssetRef> search(@RequestParam(name = "q", required = false) String q) {
        return service.search(q);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Roles.ANY)
    public AssetDetail get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.ADMIN)
    public AssetDetail create(@Valid @RequestBody AssetDtos.CreateAssetRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Roles.ADMIN)
    public AssetDetail update(@PathVariable UUID id, @Valid @RequestBody AssetDtos.UpdateAssetRequest request) {
        return service.update(id, request);
    }

    // Shift technicians take hour-meter readings on rounds, so they may record them.
    @PostMapping("/{id}/meter-readings")
    @PreAuthorize(Roles.DO_WORK)
    public AssetDetail recordMeterReading(@PathVariable UUID id,
                                          @Valid @RequestBody AssetDtos.MeterReadingRequest request) {
        return service.recordMeterReading(id, request);
    }
}
