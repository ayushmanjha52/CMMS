package com.plantdesk.asset;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AssetRepository extends JpaRepository<Asset, UUID> {

    /** The whole tree in one statement; ordering by tag puts every parent before its children. */
    @Query("select a from Asset a order by a.tag")
    List<Asset> findAllForTree();

    List<Asset> findByTagIn(Collection<String> tags);

    List<Asset> findByParentIdOrderByTag(UUID parentId);

    @Query("""
            select a from Asset a
            where lower(a.tag) like lower(concat('%', :q, '%'))
               or lower(a.name) like lower(concat('%', :q, '%'))
            order by a.tag""")
    List<Asset> search(@Param("q") String q, Pageable pageable);
}
