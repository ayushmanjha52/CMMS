package com.plantdesk.part;

import com.plantdesk.common.NotFoundException;
import com.plantdesk.security.Roles;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/parts")
public class SparePartController {

    private final SparePartRepository parts;
    private final Clock clock;

    public SparePartController(SparePartRepository parts, Clock clock) {
        this.parts = parts;
        this.clock = clock;
    }

    public record PartView(UUID id, String partNumber, String description, UnitOfMeasure unit,
                           BigDecimal stockQty, BigDecimal reorderPoint, BigDecimal unitCost,
                           String binLocation, boolean belowReorderPoint) {
        static PartView of(SparePart p) {
            return new PartView(p.getId(), p.getPartNumber(), p.getDescription(), p.getUnit(), p.getStockQty(),
                    p.getReorderPoint(), p.getUnitCost(), p.getBinLocation(), p.isBelowReorderPoint());
        }
    }

    public record CreatePartRequest(@NotBlank @Size(max = 64) String partNumber,
                                    @NotBlank @Size(max = 300) String description,
                                    @NotNull UnitOfMeasure unit,
                                    @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal unitCost,
                                    @NotNull @DecimalMin("0.000") @Digits(integer = 9, fraction = 3) BigDecimal reorderPoint,
                                    @NotNull @DecimalMin("0.000") @Digits(integer = 9, fraction = 3) BigDecimal openingStock,
                                    @Size(max = 32) String binLocation) {}

    public record ReceiptRequest(@NotNull @DecimalMin(value = "0.001") @Digits(integer = 9, fraction = 3) BigDecimal quantity,
                                 @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal unitCost) {}

    @GetMapping
    @PreAuthorize(Roles.ANY)
    @Transactional(readOnly = true)
    public List<PartView> list(@RequestParam(name = "lowStock", defaultValue = "false") boolean lowStock) {
        return parts.findAllByOrderByPartNumberAsc().stream()
                .filter(p -> !lowStock || p.isBelowReorderPoint())
                .map(PartView::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.MANAGE_WORK)
    @Transactional
    public PartView create(@Valid @RequestBody CreatePartRequest req) {
        SparePart part = new SparePart(req.partNumber(), req.description(), req.unit(), req.unitCost(),
                req.reorderPoint(), req.binLocation(), clock.instant());
        if (req.openingStock().signum() > 0) {
            part.receive(req.openingStock(), null);
        }
        return PartView.of(parts.save(part));
    }

    @PostMapping("/{id}/receipts")
    @PreAuthorize(Roles.MANAGE_WORK)
    @Transactional
    public PartView receive(@PathVariable UUID id, @Valid @RequestBody ReceiptRequest req) {
        SparePart part = parts.findById(id).orElseThrow(() -> new NotFoundException("Spare part", id));
        part.receive(req.quantity(), req.unitCost());
        return PartView.of(part);
    }
}
