package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.PlanVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlanVersionRepository extends JpaRepository<PlanVersion, Long> {

    List<PlanVersion> findByPackageIdOrderByVersionDesc(Long packageId);

    Optional<PlanVersion> findFirstByPackageIdOrderByVersionDesc(Long packageId);

    @Query("""
            select distinct v from PlanVersion v
            left join fetch v.items item
            left join fetch item.product
            where v.id = :id
            """)
    Optional<PlanVersion> findByIdWithItems(@Param("id") Long id);

    /** Live purchases per version of a package: [versionId, count]. */
    @Query(value = """
            select plan_version_id, count(*) from purchases
            where package_id = :packageId and status = 'ACTIVE' and plan_version_id is not null
            group by plan_version_id
            """, nativeQuery = true)
    List<Object[]> countActivePurchasesByVersion(@Param("packageId") Long packageId);

    @Modifying
    @Query(value = """
            update purchases set plan_version_id = :toVersionId
            where package_id = :packageId and status = 'ACTIVE'
              and (plan_version_id is null or plan_version_id <> :toVersionId)
              and (cast(:fromVersionId as bigint) is null or plan_version_id = cast(:fromVersionId as bigint))
            """, nativeQuery = true)
    int movePurchases(@Param("packageId") Long packageId,
                      @Param("fromVersionId") Long fromVersionId,
                      @Param("toVersionId") Long toVersionId);
}
