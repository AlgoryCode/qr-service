package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.CouponLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponLogRepository extends JpaRepository<CouponLog, Long> {

    Page<CouponLog> findByCouponIdOrderByCreatedAtDesc(Long couponId, Pageable pageable);
}
