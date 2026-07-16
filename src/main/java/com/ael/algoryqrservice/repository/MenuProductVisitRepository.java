package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.MenuProductVisit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface MenuProductVisitRepository extends JpaRepository<MenuProductVisit, Long> {

    @Query("SELECT COUNT(v) FROM MenuProductVisit v WHERE v.menuProductId = :menuProductId AND v.visitedAt BETWEEN :from AND :to")
    long countByMenuProductIdAndPeriod(
            @Param("menuProductId") Long menuProductId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("SELECT COUNT(DISTINCT v.ipAddress) FROM MenuProductVisit v WHERE v.menuProductId = :menuProductId AND v.visitedAt BETWEEN :from AND :to")
    long countDistinctIpByMenuProductIdAndPeriod(
            @Param("menuProductId") Long menuProductId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("SELECT v.deviceType, COUNT(v) FROM MenuProductVisit v WHERE v.menuProductId = :menuProductId AND v.visitedAt BETWEEN :from AND :to GROUP BY v.deviceType")
    List<Object[]> countByDeviceTypeAndPeriod(
            @Param("menuProductId") Long menuProductId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query(value = "SELECT DATE(visited_at) AS day, COUNT(*) AS cnt FROM tbl_menu_product_visit WHERE menu_product_id = :menuProductId AND visited_at BETWEEN :from AND :to GROUP BY DATE(visited_at) ORDER BY DATE(visited_at)", nativeQuery = true)
    List<Object[]> countDailyByMenuProductIdAndPeriod(
            @Param("menuProductId") Long menuProductId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}
