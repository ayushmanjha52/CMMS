package com.plantdesk.part;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface SparePartRepository extends JpaRepository<SparePart, UUID> {

    List<SparePart> findAllByOrderByPartNumberAsc();

    /**
     * Issue stock only if enough is on the shelf, in a single statement. Read-then-write in
     * Java would let two technicians both draw the last bearing. Returns rows updated: 0
     * means insufficient stock.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update SparePart p set p.stockQty = p.stockQty - :qty, p.version = p.version + 1
            where p.id = :id and p.stockQty >= :qty""")
    int issue(@Param("id") UUID id, @Param("qty") BigDecimal qty);
}
