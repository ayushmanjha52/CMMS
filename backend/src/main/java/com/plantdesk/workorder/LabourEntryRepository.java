package com.plantdesk.workorder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface LabourEntryRepository extends JpaRepository<LabourEntry, UUID> {

    @Query("select coalesce(sum(l.minutes), 0) from LabourEntry l where l.workOrderId = :workOrderId")
    long sumMinutes(@Param("workOrderId") UUID workOrderId);

    List<LabourEntry> findByWorkOrderIdOrderByWorkDateAscIdAsc(UUID workOrderId);
}
