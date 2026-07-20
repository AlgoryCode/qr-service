package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.MenuAnalyticsSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface MenuAnalyticsSessionRepository extends JpaRepository<MenuAnalyticsSession, UUID> {

    @Query("""
            select count(s) from MenuAnalyticsSession s
            where s.menuId = :menuId and s.startedAt between :from and :to
            """)
    long countByMenuIdAndPeriod(
            @Param("menuId") Long menuId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            select s.deviceType, count(s) from MenuAnalyticsSession s
            where s.menuId = :menuId and s.startedAt between :from and :to
            group by s.deviceType
            """)
    List<Object[]> countByDeviceTypeAndPeriod(
            @Param("menuId") Long menuId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query(value = """
            select cast(date_trunc('day', started_at) as date) as day, count(*)
            from tbl_menu_analytics_session
            where menu_id = :menuId and started_at between :from and :to
            group by day
            order by day
            """, nativeQuery = true)
    List<Object[]> countDailyByMenuIdAndPeriod(
            @Param("menuId") Long menuId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            select s from MenuAnalyticsSession s
            where s.menuId = :menuId and s.startedAt between :from and :to
            order by s.startedAt desc
            """)
    List<MenuAnalyticsSession> findRecentByMenuIdAndPeriod(
            @Param("menuId") Long menuId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}
