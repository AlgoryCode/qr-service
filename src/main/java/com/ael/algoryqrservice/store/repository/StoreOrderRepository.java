package com.ael.algoryqrservice.store.repository;

import com.ael.algoryqrservice.store.model.StoreOrder;
import com.ael.algoryqrservice.store.model.StoreOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

public interface StoreOrderRepository extends JpaRepository<StoreOrder, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<StoreOrder> findByIdAndMerchantId(Long id, Long merchantId);

    @EntityGraph(attributePaths = "items")
    Optional<StoreOrder> findByPublicToken(String publicToken);

    boolean existsByPublicToken(String publicToken);

    @EntityGraph(attributePaths = "items")
    @Query("""
            select storeOrder
            from StoreOrder storeOrder
            where storeOrder.merchantId = :merchantId
              and (:statuses is null or storeOrder.status in :statuses)
              and (:from is null or storeOrder.createdAt >= :from)
              and (:to is null or storeOrder.createdAt < :to)
            order by storeOrder.createdAt desc
            """)
    Page<StoreOrder> search(
            @Param("merchantId") Long merchantId,
            @Param("statuses") Collection<StoreOrderStatus> statuses,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );

    long countByMerchantIdAndStatusIn(Long merchantId, Collection<StoreOrderStatus> statuses);

    @EntityGraph(attributePaths = "items")
    Page<StoreOrder> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);
}
