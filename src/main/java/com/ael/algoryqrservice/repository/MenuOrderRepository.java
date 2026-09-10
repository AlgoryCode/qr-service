package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.MenuOrder;
import com.ael.algoryqrservice.model.enums.MenuOrderStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MenuOrderRepository extends JpaRepository<MenuOrder, Long> {

    List<MenuOrder> findByMenuIdAndStatusOrderBySubmittedAtDesc(Long menuId, MenuOrderStatus status);

    List<MenuOrder> findByMenuIdAndStatusInOrderBySubmittedAtDesc(Long menuId, Collection<MenuOrderStatus> statuses);

    List<MenuOrder> findByMenuIdInAndStatusOrderBySubmittedAtDesc(Collection<Long> menuIds, MenuOrderStatus status);

    List<MenuOrder> findByCustomerIdAndMenuIdOrderByCreatedAtDesc(Long customerId, Long menuId);

    Optional<MenuOrder> findByIdAndCustomerId(Long id, Long customerId);

    Optional<MenuOrder> findByTableSessionIdAndStatus(UUID tableSessionId, MenuOrderStatus status);

    Optional<MenuOrder> findByIdAndMenuId(Long id, Long menuId);

    Optional<MenuOrder> findByIdAndTableSessionId(Long id, UUID tableSessionId);

    List<MenuOrder> findByMenuIdAndStatusInAndSubmittedAtBetweenOrderBySubmittedAtDesc(
            Long menuId,
            Collection<MenuOrderStatus> statuses,
            LocalDateTime start,
            LocalDateTime end
    );

    List<MenuOrder> findByMenuIdInAndStatusInAndSubmittedAtBetweenOrderBySubmittedAtDesc(
            Collection<Long> menuIds,
            Collection<MenuOrderStatus> statuses,
            LocalDateTime start,
            LocalDateTime end
    );

    List<MenuOrder> findByTableIdAndSubmittedAtBetweenOrderBySubmittedAtDesc(
            Long tableId,
            LocalDateTime start,
            LocalDateTime end
    );

    List<MenuOrder> findByWaiterIdAndSubmittedAtBetweenOrderBySubmittedAtDesc(
            Long waiterId,
            LocalDateTime start,
            LocalDateTime end
    );

    List<MenuOrder> findByMenuIdAndTableIdAndStatusInOrderBySubmittedAtDesc(
            Long menuId,
            Long tableId,
            Collection<MenuOrderStatus> statuses
    );

    @EntityGraph(attributePaths = "items")
    List<MenuOrder> findByMenuIdAndStatusAndConfirmedAtBetweenOrderByConfirmedAtAsc(
            Long menuId,
            MenuOrderStatus status,
            LocalDateTime start,
            LocalDateTime end
    );

    @EntityGraph(attributePaths = "items")
    List<MenuOrder> findByMenuIdInAndStatusAndConfirmedAtBetweenOrderByConfirmedAtAsc(
            Collection<Long> menuIds,
            MenuOrderStatus status,
            LocalDateTime start,
            LocalDateTime end
    );

    @EntityGraph(attributePaths = "items")
    List<MenuOrder> findByMenuIdInAndCreatedAtBetweenOrderByCreatedAtAsc(
            Collection<Long> menuIds,
            LocalDateTime start,
            LocalDateTime end
    );

    @Query("""
            SELECT o.status, COUNT(o) FROM MenuOrder o
            WHERE o.menuId IN :menuIds
              AND o.createdAt >= :fromDt
              AND o.createdAt <= :toDt
            GROUP BY o.status
            """)
    List<Object[]> countByStatusForMenuIds(
            @Param("menuIds") Collection<Long> menuIds,
            @Param("fromDt") LocalDateTime fromDt,
            @Param("toDt") LocalDateTime toDt
    );

    @Query("""
            SELECT o.cancelledByWaiterId, COUNT(o) FROM MenuOrder o
            WHERE o.menuId IN :menuIds
              AND o.status IN :statuses
              AND COALESCE(o.cancelledAt, o.rejectedAt) >= :fromDt
              AND COALESCE(o.cancelledAt, o.rejectedAt) <= :toDt
            GROUP BY o.cancelledByWaiterId
            """)
    List<Object[]> countCancellationsByWaiter(
            @Param("menuIds") Collection<Long> menuIds,
            @Param("statuses") Collection<MenuOrderStatus> statuses,
            @Param("fromDt") LocalDateTime fromDt,
            @Param("toDt") LocalDateTime toDt
    );

    @Query("""
            SELECT COUNT(DISTINCT o.customerId) FROM MenuOrder o
            WHERE o.menuId IN :menuIds
              AND o.customerId IS NOT NULL
              AND o.createdAt >= :fromDt
              AND o.createdAt <= :toDt
            """)
    long countDistinctCustomers(
            @Param("menuIds") Collection<Long> menuIds,
            @Param("fromDt") LocalDateTime fromDt,
            @Param("toDt") LocalDateTime toDt
    );

    @Query("""
            SELECT COUNT(o) FROM MenuOrder o
            WHERE o.menuId IN :menuIds
              AND o.customerId IS NOT NULL
              AND o.createdAt >= :fromDt
              AND o.createdAt <= :toDt
            """)
    long countIdentifiedCustomerOrders(
            @Param("menuIds") Collection<Long> menuIds,
            @Param("fromDt") LocalDateTime fromDt,
            @Param("toDt") LocalDateTime toDt
    );
}