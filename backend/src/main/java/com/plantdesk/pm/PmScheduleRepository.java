package com.plantdesk.pm;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface PmScheduleRepository extends JpaRepository<PmSchedule, UUID> {

    @Query("select s from PmSchedule s join fetch s.asset where s.active = true")
    List<PmSchedule> findActiveWithAsset();

    @Query("select s from PmSchedule s join fetch s.asset order by s.asset.tag, s.title")
    List<PmSchedule> findAllWithAsset();
}
