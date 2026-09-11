package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.Coupon;
import com.ael.algoryqrservice.model.enums.CouponStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    Optional<Coupon> findByCode(String code);

    boolean existsByCode(String code);

    Optional<Coupon> findByReservedPurchaseId(Long purchaseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Coupon c where c.code = :code")
    Optional<Coupon> findByCodeForUpdate(@Param("code") String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Coupon c where c.id = :id")
    Optional<Coupon> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select c from Coupon c
            where (:status is null or c.status = :status)
              and (:q is null or lower(c.code) like lower(concat('%', :q, '%')))
            """)
    Page<Coupon> search(
            @Param("status") CouponStatus status,
            @Param("q") String query,
            Pageable pageable
    );
}
