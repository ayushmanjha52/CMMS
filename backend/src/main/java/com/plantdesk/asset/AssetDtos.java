package com.plantdesk.asset;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AssetDtos {

    private AssetDtos() {}

    public record CreateAssetRequest(
            UUID parentId,
            @NotNull AssetLevel level,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 5) String typePrefix,
            @Size(max = 100) String make,
            @Size(max = 100) String model,
            @Size(max = 100) String rating,
            @Size(max = 100) String serialNumber,
            Criticality criticality,
            LocalDate commissionedOn) {}

    public record UpdateAssetRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 100) String make,
            @Size(max = 100) String model,
            @Size(max = 100) String rating,
            @Size(max = 100) String serialNumber,
            @NotNull Criticality criticality,
            LocalDate commissionedOn,
            boolean inService) {}

    /** Hour meters are read in hours with one decimal; stored as integer minutes. */
    public record MeterReadingRequest(@NotNull @DecimalMin("0.0") BigDecimal runningHours) {
        long toMinutes() {
            return runningHours.multiply(BigDecimal.valueOf(60)).setScale(0, RoundingMode.HALF_UP).longValueExact();
        }
    }

    public record AssetRef(UUID id, String tag, String name, AssetLevel level) {
        static AssetRef of(Asset a) {
            return new AssetRef(a.getId(), a.getTag(), a.getName(), a.getLevel());
        }
    }

    public record AssetDetail(
            UUID id, UUID parentId, String tag, String code, String name, AssetLevel level,
            String make, String model, String rating, String serialNumber,
            Criticality criticality, BigDecimal runningHours, Instant runningUpdatedAt,
            LocalDate commissionedOn, boolean inService,
            List<AssetRef> path, List<AssetRef> children) {

        static AssetDetail of(Asset a, List<AssetRef> path, List<AssetRef> children) {
            return new AssetDetail(a.getId(), a.getParentId(), a.getTag(), a.getCode(), a.getName(), a.getLevel(),
                    a.getMake(), a.getModel(), a.getRating(), a.getSerialNumber(), a.getCriticality(),
                    hours(a.getRunningMinutes()), a.getRunningUpdatedAt(), a.getCommissionedOn(), a.isInService(),
                    path, children);
        }
    }

    public record TreeNode(UUID id, String tag, String code, String name, AssetLevel level,
                           Criticality criticality, boolean inService, List<TreeNode> children) {
        static TreeNode of(Asset a) {
            return new TreeNode(a.getId(), a.getTag(), a.getCode(), a.getName(), a.getLevel(),
                    a.getCriticality(), a.isInService(), new ArrayList<>());
        }
    }

    public static BigDecimal hours(long minutes) {
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 1, RoundingMode.HALF_UP);
    }
}
