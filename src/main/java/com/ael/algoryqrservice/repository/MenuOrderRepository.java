package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.MenuOrder;
import com.ael.algoryqrservice.model.enums.MenuOrderStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MenuOrderRepository extends JpaRepository<MenuOrder, Long> {

    List<MenuOrder> findByMenuIdAndStatusOrderBySubmittedAtDesc(Long menuId, MenuOrderStatus status);

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
}
