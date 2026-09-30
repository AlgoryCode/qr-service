package com.ael.algoryqrservice.store.repository;

import com.ael.algoryqrservice.store.model.StoreOrder;
import com.ael.algoryqrservice.store.model.StoreOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StoreOrderRepository extends JpaRepository<StoreOrder, Long>, JpaSpecificationExecutor<StoreOrder> {

    @EntityGraph(attributePaths = "items")
    Optional<StoreOrder> findByIdAndMerchantId(Long id, Long merchantId);

    @EntityGraph(attributePaths = "items")
    Optional<StoreOrder> findByPublicToken(String publicToken);

    boolean existsByPublicToken(String publicToken);

    long countByMerchantIdAndStatusIn(Long merchantId, Collection<StoreOrderStatus> statuses);

    @Query("select count(o) > 0 from StoreOrder o where o.merchantId = :merchantId and o.status in :statuses "
            + "and (o.branchId is null or o.branchId = :branchId)")
    boolean existsForBranch(
            @Param("merchantId") Long merchantId,
            @Param("branchId") Long branchId,
            @Param("statuses") Collection<StoreOrderStatus> statuses
    );

    List<StoreOrder> findByMerchantIdAndCourierStaffIdInAndStatusIn(
            Long merchantId,
            Collection<Long> courierStaffIds,
            Collection<StoreOrderStatus> statuses
    );

    @EntityGraph(attributePaths = "items")
    Page<StoreOrder> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);
}
