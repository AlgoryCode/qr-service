package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.DeviceCustomer;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DeviceCustomerRepository extends JpaRepository<DeviceCustomer, Long> {

    Optional<DeviceCustomer> findByIdAndMerchantIdAndDeletedFalse(Long id, Long merchantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from DeviceCustomer c where c.id = :id and c.merchantId = :merchantId and c.deleted = false")
    Optional<DeviceCustomer> lockOwned(@Param("id") Long id, @Param("merchantId") Long merchantId);

    boolean existsByMerchantIdAndPhoneAndDeletedFalse(Long merchantId, String phone);

    boolean existsByMerchantIdAndPhoneAndDeletedFalseAndIdNot(Long merchantId, String phone, Long id);

    List<DeviceCustomer> findByIdIn(Collection<Long> ids);

    /** Name or phone contains {@code q} (lower-cased by the caller); an empty {@code q} lists everyone. */
    @Query("""
            select c from DeviceCustomer c
            where c.merchantId = :merchantId and c.deleted = false
              and (:q = '' or lower(c.name) like concat('%', :q, '%') or c.phone like concat('%', :q, '%'))
            order by c.name asc, c.id asc
            """)
    Page<DeviceCustomer> search(@Param("merchantId") Long merchantId, @Param("q") String q, Pageable pageable);

    Optional<DeviceCustomer> findByMerchantIdAndReferralCodeAndDeletedFalse(Long merchantId, String referralCode);

    boolean existsByMerchantIdAndReferralCode(Long merchantId, String referralCode);

    /** Best customers by total spending. */
    @Query("""
            select c from DeviceCustomer c
            where c.merchantId = :merchantId and c.deleted = false and c.totalSpent > 0
            order by c.totalSpent desc, c.id asc
            """)
    List<DeviceCustomer> findTopSpenders(@Param("merchantId") Long merchantId, Pageable pageable);

    /** Customers who came before but not since {@code before}, best first. */
    @Query("""
            select c from DeviceCustomer c
            where c.merchantId = :merchantId and c.deleted = false and c.visits > 0
              and c.lastVisitAt is not null and c.lastVisitAt < :before
            order by c.totalSpent desc, c.lastVisitAt desc, c.id asc
            """)
    List<DeviceCustomer> findLapsed(
            @Param("merchantId") Long merchantId,
            @Param("before") java.time.LocalDateTime before,
            Pageable pageable
    );
}
