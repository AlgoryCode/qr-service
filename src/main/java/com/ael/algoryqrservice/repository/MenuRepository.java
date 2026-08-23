package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface MenuRepository extends JpaRepository<Menu, Long> {
    Optional<Menu> findByQrIdAndDeletedFalse(Long qrId);

    Optional<Menu> findByQrIdAndActiveTrueAndDeletedFalse(Long qrId);

    @Query("""
            select menu, qr
            from Menu menu, Qr qr
            where menu.qrId = :qrId
              and qr.qrId = menu.qrId
              and menu.active = true
              and menu.deleted = false
              and qr.deleted = false
            """)
    List<Object[]> findActiveMenuWithQrByQrId(@Param("qrId") Long qrId);

    @Query("""
            select menu, qr
            from Menu menu, Qr qr
            where menu.userId = :userId
              and menu.qrId = qr.qrId
              and menu.active = true
              and menu.deleted = false
              and qr.deleted = false
            order by menu.menuId desc
            """)
    List<Object[]> findActiveMenusWithQrByUserId(@Param("userId") Long userId);

    @Query("""
            select menu.qrId
            from Menu menu
            where menu.userId = :userId
              and menu.qrId in :qrIds
              and menu.active = true
              and menu.deleted = false
            """)
    Set<Long> findActiveQrIdsByUserIdAndQrIdIn(@Param("userId") Long userId, @Param("qrIds") Collection<Long> qrIds);

    @Query("""
            select case when count(menu) > 0 then true else false end
            from Menu menu, Qr qr
            where menu.userId = :userId
              and menu.qrId = qr.qrId
              and menu.active = true
              and menu.deleted = false
              and qr.deleted = false
            """)
    boolean existsActiveLiveMenuQrForUser(@Param("userId") Long userId);

    @Query("""
            select count(menu)
            from Menu menu, Qr qr
            where menu.userId = :userId
              and menu.qrId = qr.qrId
              and menu.active = true
              and menu.deleted = false
              and qr.deleted = false
            """)
    long countActiveLiveMenusForUser(@Param("userId") Long userId);

    @Query("""
            select count(menu)
            from Menu menu, Qr qr
            where menu.branchId = :branchId
              and menu.qrId = qr.qrId
              and menu.active = true
              and menu.deleted = false
              and qr.deleted = false
            """)
    long countActiveLiveMenusForBranch(@Param("branchId") Long branchId);

    @Query("""
            select menu.branchId, count(menu)
            from Menu menu, Qr qr
            where menu.userId = :userId
              and menu.qrId = qr.qrId
              and menu.active = true
              and menu.deleted = false
              and qr.deleted = false
              and menu.branchId is not null
            group by menu.branchId
            """)
    List<Object[]> countActiveLiveMenusGroupedByBranch(@Param("userId") Long userId);

    List<Menu> findByUserIdAndDeletedFalseOrderByMenuIdAsc(Long userId);

    List<Menu> findByBranchIdAndDeletedFalse(Long branchId);

    List<Menu> findByUserIdAndBranchIdIsNullAndDeletedFalse(Long userId);

    @Query("""
            select distinct menu.userId
            from Menu menu
            where menu.deleted = false
            """)
    List<Long> findDistinctUserIdsByDeletedFalse();

    @Query("""
            select menu.menuId
            from Menu menu
            where menu.userId = :userId
              and menu.deleted = false
            """)
    List<Long> findMenuIdsByUserId(@Param("userId") Long userId);

    @Modifying(clearAutomatically = false, flushAutomatically = true)
    @Query("""
            update Menu menu
            set menu.publicAccessEnabled = :enabled,
                menu.publicAccessDisabledReason = :reason
            where menu.userId = :userId
              and menu.deleted = false
            """)
    int updatePublicAccessByUserId(
            @Param("userId") Long userId,
            @Param("enabled") boolean enabled,
            @Param("reason") String reason
    );

    @Modifying(clearAutomatically = false, flushAutomatically = true)
    @Query("""
            update Menu menu
            set menu.active = false
            where menu.userId = :userId
              and menu.deleted = false
              and menu.active = true
            """)
    int deactivateActiveMenusByUserId(@Param("userId") Long userId);
}
