package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.SiteVisit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SiteVisitRepository extends JpaRepository<SiteVisit, Long> {

    Page<SiteVisit> findByCreatedAtBetweenOrderByCreatedAtDesc(
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable
    );

    long countByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    @Query("""
            select v.deviceType, count(v)
            from SiteVisit v
            where v.createdAt between :from and :to
            group by v.deviceType
            order by count(v) desc
            """)
    List<Object[]> countByDeviceTypeBetween(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            select coalesce(v.countryName, 'Bilinmiyor'), count(v)
            from SiteVisit v
            where v.createdAt between :from and :to
            group by coalesce(v.countryName, 'Bilinmiyor')
            order by count(v) desc
            """)
    List<Object[]> countByCountryBetween(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            select cast(v.createdAt as date), count(v)
            from SiteVisit v
            where v.createdAt between :from and :to
            group by cast(v.createdAt as date)
            order by cast(v.createdAt as date)
            """)
    List<Object[]> countByDayBetween(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}
