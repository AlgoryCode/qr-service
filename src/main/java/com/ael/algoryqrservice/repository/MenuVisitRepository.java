package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.MenuVisit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface MenuVisitRepository extends JpaRepository<MenuVisit, Long> {

    @Query("SELECT COUNT(v) FROM MenuVisit v WHERE v.menuId = :menuId AND v.visitedAt BETWEEN :from AND :to")
    long countByMenuIdAndPeriod(
            @Param("menuId") Long menuId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("SELECT COUNT(DISTINCT v.ipAddress) FROM MenuVisit v WHERE v.menuId = :menuId AND v.visitedAt BETWEEN :from AND :to")
    long countDistinctIpByMenuIdAndPeriod(
            @Param("menuId") Long menuId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("SELECT v.deviceType, COUNT(v) FROM MenuVisit v WHERE v.menuId = :menuId AND v.visitedAt BETWEEN :from AND :to GROUP BY v.deviceType")
    List<Object[]> countByDeviceTypeAndPeriod(
            @Param("menuId") Long menuId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query(value = "SELECT DATE(visited_at) AS day, COUNT(*) AS cnt FROM tbl_menu_visit WHERE menu_id = :menuId AND visited_at BETWEEN :from AND :to GROUP BY DATE(visited_at) ORDER BY DATE(visited_at)", nativeQuery = true)
    List<Object[]> countDailyByMenuIdAndPeriod(
            @Param("menuId") Long menuId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}
