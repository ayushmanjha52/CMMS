package com.plantdesk.workorder;

import com.plantdesk.asset.AssetDtos;
import com.plantdesk.asset.Criticality;
import com.plantdesk.user.Trade;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class WorkOrderDtos {

    private WorkOrderDtos() {}

    public record RaiseRequest(
            @NotNull UUID assetId,
            @NotNull WorkOrderType type,
            @NotNull Priority priority,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 4000) String description,
            FailureCode failureCode,
            Instant failedAt,
            UUID assigneeId) {}

    public record AssignRequest(@NotNull UUID technicianId) {}

    public record TransitionRequest(@NotNull WorkOrderAction action, @Size(max = 2000) String note, FailureCode failureCode) {}

    public record LabourRequest(@Min(1) @Max(1440) int minutes, @NotNull @PastOrPresent LocalDate workDate,
                                @Size(max = 300) String note, UUID technicianId) {}

    public record PartRequest(@NotNull UUID sparePartId, @NotNull @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal quantity) {}

    public record DowntimeRequest(@NotNull Instant start, Instant end) {}

    public record PersonRef(UUID id, String name, Trade trade) {}

    public record AssetSummary(UUID id, String tag, String name, Criticality criticality) {}

    public record ListItem(UUID id, String number, WorkOrderType type, Priority priority, WorkOrderStatus status,
                           String title, AssetSummary asset, PersonRef assignee, Instant raisedAt,
                           Instant dueAt, boolean overdue) {}

    public record LabourLine(UUID id, PersonRef technician, int minutes, LocalDate workDate, String note) {}

    public record PartLine(UUID id, UUID sparePartId, String partNumber, String description, String unit,
                           BigDecimal quantity, BigDecimal unitCost, BigDecimal lineCost, Instant consumedAt) {}

    public record FailureCodeView(String code, String description, boolean countsAsFailure) {}

    public record Detail(
            UUID id, String number, WorkOrderType type, Priority priority, WorkOrderStatus status,
            String title, String description, FailureCodeView failureCode,
            AssetSummary asset, PersonRef assignee,
            Instant raisedAt, Instant startedAt, Instant completedAt, Instant closedAt,
            Instant downtimeStart, Instant downtimeEnd, Long downtimeMinutes,
            String holdReason, String closureNote,
            UUID pmScheduleId, Instant dueAt, BigDecimal dueRunningHours, boolean overdue,
            List<LabourLine> labour, long labourMinutesTotal,
            List<PartLine> parts, BigDecimal partsCostTotal,
            Set<WorkOrderAction> availableActions) {}

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        public static <T> PageResponse<T> of(Page<T> page) {
            return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages());
        }
    }

    static AssetSummary assetSummary(com.plantdesk.asset.Asset a) {
        return new AssetSummary(a.getId(), a.getTag(), a.getName(), a.getCriticality());
    }

    static PersonRef person(com.plantdesk.user.User u) {
        return u == null ? null : new PersonRef(u.getId(), u.getFullName(), u.getTrade());
    }

    static FailureCodeView failureCode(FailureCode c) {
        return c == null ? null : new FailureCodeView(c.name(), c.description(), c.countsAsFailure());
    }

    static BigDecimal hours(Long minutes) {
        return minutes == null ? null : AssetDtos.hours(minutes);
    }
}
