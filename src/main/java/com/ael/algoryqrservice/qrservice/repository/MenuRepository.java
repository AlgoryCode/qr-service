package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.Menu;
import com.ael.algoryqrservice.qrservice.model.enums.MenuChannel;
import com.ael.algoryqrservice.qrservice.model.enums.MenuPublicAccessDisabledReason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import com.ael.algoryqrservice.qrservice.qr.model.Qr;

public interface MenuRepository extends JpaRepository<Menu, Long> {
    Optional<Menu> findByQrIdAndDeletedFalse(Long qrId);

    Optional<Menu> findByQrIdAndActiveTrueAndDeletedFalse(Long qrId);

    Optional<Menu> findByPublicIdAndDeletedFalse(String publicId);

    Optional<Menu> findByPublicIdAndActiveTrueAndDeletedFalse(String publicId);

    boolean existsByPublicId(String publicId);

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
            where menu.merchantId = :merchantId
              and menu.qrId = qr.qrId
              and menu.active = true
              and menu.deleted = false
              and qr.deleted = false
            order by menu.menuId desc
            """)
    List<Object[]> findActiveMenusWithQrByMerchantId(@Param("merchantId") Long merchantId);

    @Query("""
            select menu, qr
            from Menu menu, Qr qr
            where menu.merchantId = :merchantId
              and menu.qrId = qr.qrId
              and menu.deleted = false
              and qr.deleted = false
            order by menu.menuId desc
            """)
    List<Object[]> findMenusWithQrByMerchantId(@Param("merchantId") Long merchantId);

    @Query("""
            select menu.qrId
            from Menu menu
            where menu.merchantId = :merchantId
              and menu.qrId in :qrIds
              and menu.active = true
              and menu.deleted = false
            """)
    Set<Long> findActiveQrIdsByMerchantIdAndQrIdIn(@Param("merchantId") Long merchantId, @Param("qrIds") Collection<Long> qrIds);

    @Query("""
            select case when count(menu) > 0 then true else false end
            from Menu menu, Qr qr
            where menu.merchantId = :merchantId
              and menu.qrId = qr.qrId
              and menu.active = true
              and menu.deleted = false
              and qr.deleted = false
            """)
    boolean existsActiveLiveMenuQrForMerchant(@Param("merchantId") Long merchantId);

    @Query("""
            select count(menu)
            from Menu menu, Qr qr
            where menu.merchantId = :merchantId
              and menu.qrId = qr.qrId
              and menu.active = true
              and menu.deleted = false
              and qr.deleted = false
            """)
    long countActiveLiveMenusForMerchant(@Param("merchantId") Long merchantId);

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
            where menu.merchantId = :merchantId
              and menu.qrId = qr.qrId
              and menu.active = true
              and menu.deleted = false
              and qr.deleted = false
              and menu.branchId is not null
            group by menu.branchId
            """)
    List<Object[]> countActiveLiveMenusGroupedByBranch(@Param("merchantId") Long merchantId);

    List<Menu> findByMerchantIdAndChannelAndDeletedFalseOrderByMenuIdAsc(Long merchantId, MenuChannel channel);

    List<Menu> findByBranchIdAndChannelAndDeletedFalse(Long branchId, MenuChannel channel);

    @Query("""
            select menu.menuId
            from Menu menu
            where menu.merchantId = :merchantId
              and menu.deleted = false
              and menu.channel = :channel
            """)
    List<Long> findMenuIdsByMerchantIdAndChannel(@Param("merchantId") Long merchantId, @Param("channel") MenuChannel channel);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Menu menu
            set menu.publicAccessEnabled = :enabled,
                menu.publicAccessDisabledReason = :reason
            where menu.merchantId = :merchantId
              and menu.deleted = false
            """)
    int updatePublicAccessByMerchantId(
            @Param("merchantId") Long merchantId,
            @Param("enabled") boolean enabled,
            @Param("reason") MenuPublicAccessDisabledReason reason
    );

    @Modifying(clearAutomatically = false, flushAutomatically = true)
    @Query("""
            update Menu menu
            set menu.active = false
            where menu.merchantId = :merchantId
              and menu.deleted = false
              and menu.active = true
            """)
    int deactivateActiveMenusByMerchantId(@Param("merchantId") Long merchantId);
}
