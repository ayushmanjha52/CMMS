package com.plantdesk.workorder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PartConsumptionRepository extends JpaRepository<PartConsumption, UUID> {

    List<PartConsumption> findByWorkOrderIdOrderByConsumedAtAsc(UUID workOrderId);
}
