package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public interface MenuRepository extends JpaRepository<Menu, Long> {
    Optional<Menu> findByQrIdAndDeletedFalse(Long qrId);

    Optional<Menu> findByQrIdAndActiveTrueAndDeletedFalse(Long qrId);

    Optional<Menu> findByPublicSlugIgnoreCaseAndDeletedFalse(String publicSlug);

    Optional<Menu> findByPublicSlugIgnoreCaseAndActiveTrueAndDeletedFalse(String publicSlug);

    boolean existsByPublicSlugIgnoreCaseAndDeletedFalse(String publicSlug);

    boolean existsByPublicSlugIgnoreCaseAndDeletedFalseAndMenuIdNot(String publicSlug, Long menuId);

    @Query("""
            select menu.qrId
            from Menu menu
            where menu.userId = :userId
              and menu.qrId in :qrIds
              and menu.active = true
              and menu.deleted = false
            """)
    Set<Long> findActiveQrIdsByUserIdAndQrIdIn(@Param("userId") Long userId, @Param("qrIds") Collection<Long> qrIds);
}
