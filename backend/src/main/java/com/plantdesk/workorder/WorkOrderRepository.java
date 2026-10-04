package com.plantdesk.workorder;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, UUID>, JpaSpecificationExecutor<WorkOrder> {

    /**
     * The list endpoint. The entity graph turns asset and assignee into joins in the same
     * SELECT; without it, each distinct asset and assignee on the page is a separate lazy
     * load (measured: 24 statements for 20 rows). QueryCountIT holds this to two (page + count).
     */
    @Override
    @EntityGraph(attributePaths = {"asset", "assignee"})
    Page<WorkOrder> findAll(Specification<WorkOrder> spec, Pageable pageable);

    @Query("select w from WorkOrder w join fetch w.asset left join fetch w.assignee where w.id = :id")
    Optional<WorkOrder> findDetailed(@Param("id") UUID id);

    @Query("select distinct w.pmScheduleId from WorkOrder w where w.pmScheduleId is not null and w.status in :statuses")
    List<UUID> findPmScheduleIdsWithStatusIn(@Param("statuses") Collection<WorkOrderStatus> statuses);

    @Query("select w from WorkOrder w join fetch w.asset where w.pmScheduleId is not null and w.status in :statuses")
    List<WorkOrder> findPmOrdersWithStatusIn(@Param("statuses") Collection<WorkOrderStatus> statuses);

    /** Breakdowns whose downtime began inside [from, to). Cancelled orders were never failures. */
    @Query("""
            select w from WorkOrder w join fetch w.asset
            where w.type = com.plantdesk.workorder.WorkOrderType.BREAKDOWN
              and w.status <> com.plantdesk.workorder.WorkOrderStatus.CANCELLED
              and w.downtimeStart >= :from and w.downtimeStart < :to
            order by w.downtimeStart""")
    List<WorkOrder> findBreakdownsBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select w from WorkOrder w join fetch w.asset
            where w.asset.id = :assetId
              and w.type = com.plantdesk.workorder.WorkOrderType.BREAKDOWN
              and w.status <> com.plantdesk.workorder.WorkOrderStatus.CANCELLED
              and w.downtimeStart >= :from and w.downtimeStart < :to
            order by w.downtimeStart""")
    List<WorkOrder> findBreakdownsForAssetBetween(@Param("assetId") UUID assetId,
                                                  @Param("from") Instant from, @Param("to") Instant to);

    @Query("select w from WorkOrder w join fetch w.asset where w.status in :statuses")
    List<WorkOrder> findWithStatusIn(@Param("statuses") Collection<WorkOrderStatus> statuses);
}
