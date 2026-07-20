package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.MenuAnalyticsEvent;
import com.ael.algoryqrservice.model.enums.MenuAnalyticsEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MenuAnalyticsEventRepository extends JpaRepository<MenuAnalyticsEvent, Long> {

    long countByMenuIdAndEventTypeAndOccurredAtBetween(
            Long menuId,
            MenuAnalyticsEventType eventType,
            LocalDateTime from,
            LocalDateTime to
    );

    @Query(value = """
            select cast(date_trunc('day', occurred_at) as date) as day,
                   count(*) filter (where event_type = 'MENU_OPEN'),
                   count(*) filter (where event_type = 'PRODUCT_VIEW')
            from tbl_menu_analytics_event
            where menu_id = :menuId and occurred_at between :from and :to
            group by day
            order by day
            """, nativeQuery = true)
    List<Object[]> countDailyOpenAndProductByMenuId(
            @Param("menuId") Long menuId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query(value = """
            select extract(hour from occurred_at) as hour_of_day, count(*)
            from tbl_menu_analytics_event
            where menu_id = :menuId and occurred_at between :from and :to
            group by hour_of_day
            order by hour_of_day
            """, nativeQuery = true)
    List<Object[]> countHourlyByMenuId(
            @Param("menuId") Long menuId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            select e.productId, count(e) from MenuAnalyticsEvent e
            where e.menuId = :menuId
              and e.eventType = com.ael.algoryqrservice.model.enums.MenuAnalyticsEventType.PRODUCT_VIEW
              and e.occurredAt between :from and :to
              and e.productId is not null
            group by e.productId
            order by count(e) desc
            """)
    List<Object[]> topProducts(
            @Param("menuId") Long menuId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            select e.categoryId, count(e) from MenuAnalyticsEvent e
            where e.menuId = :menuId
              and e.eventType = com.ael.algoryqrservice.model.enums.MenuAnalyticsEventType.CATEGORY_VIEW
              and e.occurredAt between :from and :to
              and e.categoryId is not null
            group by e.categoryId
            order by count(e) desc
            """)
    List<Object[]> topCategories(
            @Param("menuId") Long menuId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            select e.categoryId, e.productId, count(e) from MenuAnalyticsEvent e
            where e.menuId = :menuId
              and e.eventType = com.ael.algoryqrservice.model.enums.MenuAnalyticsEventType.PRODUCT_VIEW
              and e.occurredAt between :from and :to
              and e.productId is not null
            group by e.categoryId, e.productId
            order by count(e) desc
            """)
    List<Object[]> productViewsByCategory(
            @Param("menuId") Long menuId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query(value = """
            select avg(cnt)::float8 from (
                select count(*) as cnt
                from tbl_menu_analytics_event
                where menu_id = :menuId
                  and event_type = 'PRODUCT_VIEW'
                  and occurred_at between :from and :to
                group by session_id
            ) session_counts
            """, nativeQuery = true)
    Double avgProductsPerSession(
            @Param("menuId") Long menuId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    List<MenuAnalyticsEvent> findBySessionIdInOrderBySessionIdAscSequenceAsc(Collection<UUID> sessionIds);

    long countBySessionIdAndMenuId(UUID sessionId, Long menuId);
}
