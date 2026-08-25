package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.FulfillmentDetail;
import com.ael.algoryqrservice.model.enums.FulfillmentDetailSource;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FulfillmentDetailRepository extends JpaRepository<FulfillmentDetail, Long> {

    /** Detail is inside its own validity window and its grant is still ACTIVE. */
    String ACTIVE_DETAIL =
            " AND (d.startsAt IS NULL OR d.startsAt <= :now)"
            + " AND (d.expiresAt IS NULL OR d.expiresAt > :now)"
            + " AND EXISTS (SELECT f FROM GrantFulfillment f WHERE f.id = d.fulfillmentId"
            + " AND f.status = com.ael.algoryqrservice.model.enums.GrantFulfillmentStatus.ACTIVE)";

    String ADDON_SOURCE =
            " AND d.source = com.ael.algoryqrservice.model.enums.FulfillmentDetailSource.ADDON_PURCHASE";

    List<FulfillmentDetail> findByFulfillmentId(Long fulfillmentId);

    @Query("SELECT d FROM FulfillmentDetail d WHERE d.userId = :userId AND d.scopeCode = :scopeCode" + ACTIVE_DETAIL)
    List<FulfillmentDetail> findActiveByScopeCode(
            @Param("userId") Long userId,
            @Param("scopeCode") String scopeCode,
            @Param("now") LocalDateTime now
    );

    @Query("SELECT COUNT(d) > 0 FROM FulfillmentDetail d WHERE d.userId = :userId AND d.scopeCode = :scopeCode"
           + ACTIVE_DETAIL)
    boolean existsActiveByScopeCode(
            @Param("userId") Long userId,
            @Param("scopeCode") String scopeCode,
            @Param("now") LocalDateTime now
    );

    @Query("SELECT COALESCE(SUM(d.quantity), 0) FROM FulfillmentDetail d WHERE d.userId = :userId "
           + "AND d.featureCode = :featureCode" + ADDON_SOURCE + ACTIVE_DETAIL)
    int sumActiveAddonQuantityByFeatureCode(
            @Param("userId") Long userId,
            @Param("featureCode") String featureCode,
            @Param("now") LocalDateTime now
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM FulfillmentDetail d WHERE d.userId = :userId "
           + "AND d.featureCode = :featureCode AND d.source = :source" + ACTIVE_DETAIL
           + " ORDER BY d.expiresAt ASC")
    List<FulfillmentDetail> findAndLockActiveByFeatureCodeAndSource(
            @Param("userId") Long userId,
            @Param("featureCode") String featureCode,
            @Param("source") FulfillmentDetailSource source,
            @Param("now") LocalDateTime now
    );

    @Query("""
            SELECT d FROM FulfillmentDetail d
            WHERE d.fulfillmentId IN (
                    SELECT f.id FROM GrantFulfillment f
                    WHERE f.userId = :userId
                      AND f.status = com.ael.algoryqrservice.model.enums.GrantFulfillmentStatus.ACTIVE)
              AND (d.startsAt IS NULL OR d.startsAt <= :now)
              AND (d.expiresAt IS NULL OR d.expiresAt > :now)
            ORDER BY d.source ASC, d.featureCode ASC
            """)
    List<FulfillmentDetail> findAllActiveByUserId(
            @Param("userId") Long userId,
            @Param("now") LocalDateTime now
    );

    Optional<FulfillmentDetail> findByFulfillmentIdAndFeatureCodeAndSource(
            Long fulfillmentId, String featureCode, FulfillmentDetailSource source
    );

    List<FulfillmentDetail> findByFulfillmentIdAndSource(Long fulfillmentId, FulfillmentDetailSource source);
}
