package com.ael.algoryqrservice.integration.yemeksepeti.repository;

import com.ael.algoryqrservice.integration.yemeksepeti.model.YemekSepetiOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface YemekSepetiOrderRepository
        extends JpaRepository<YemekSepetiOrder, Long>, JpaSpecificationExecutor<YemekSepetiOrder> {

    Optional<YemekSepetiOrder> findByConnectionIdAndExternalOrderId(Long connectionId, String externalOrderId);

    Optional<YemekSepetiOrder> findByIdAndConnectionId(Long id, Long connectionId);

    List<YemekSepetiOrder> findByConnectionIdAndPackageCreatedAtBetweenOrderByPackageCreatedAtDesc(
            Long connectionId,
            LocalDateTime from,
            LocalDateTime to
    );

    @Query("""
            SELECT o
            FROM YemekSepetiOrder o
            WHERE o.connectionId = :connectionId
              AND LOWER(o.packageStatus) IN :statuses
            ORDER BY o.packageCreatedAt DESC
            """)
    List<YemekSepetiOrder> findKitchenOrders(
            @Param("connectionId") Long connectionId,
            @Param("statuses") Collection<String> statuses
    );
}
