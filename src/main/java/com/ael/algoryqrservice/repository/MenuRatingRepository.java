package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.MenuRating;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MenuRatingRepository extends JpaRepository<MenuRating, Long> {

    Optional<MenuRating> findByMenuIdAndIpAddress(Long menuId, String ipAddress);

    long countByMenuId(Long menuId);

    @Query("""
            select coalesce(avg(r.score), 0.0)
            from MenuRating r
            where r.menuId = :menuId
            """)
    Double averageScoreByMenuId(@Param("menuId") Long menuId);

    @Query("""
            select coalesce(avg(r.score), 0.0)
            from MenuRating r
            where r.menuId = :menuId
              and r.createdAt between :from and :to
            """)
    Double averageScoreByMenuIdAndPeriod(
            @Param("menuId") Long menuId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            select count(r)
            from MenuRating r
            where r.menuId = :menuId
              and r.createdAt between :from and :to
            """)
    long countByMenuIdAndPeriod(
            @Param("menuId") Long menuId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            select r.score, count(r)
            from MenuRating r
            where r.menuId = :menuId
              and r.createdAt between :from and :to
            group by r.score
            order by r.score
            """)
    List<Object[]> scoreHistogramByMenuIdAndPeriod(
            @Param("menuId") Long menuId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            select r
            from MenuRating r
            where r.menuId = :menuId
              and r.createdAt between :from and :to
              and r.comment is not null
              and r.comment <> ''
            order by r.score asc, r.createdAt desc
            """)
    List<MenuRating> sampleCommentsByMenuIdAndPeriod(
            @Param("menuId") Long menuId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );

    @Query("""
            select r
            from MenuRating r
            where r.menuId = :menuId
              and (:from is null or r.createdAt >= :from)
              and (:to is null or r.createdAt <= :to)
              and (:minScore is null or r.score >= :minScore)
            order by r.createdAt desc
            """)
    Page<MenuRating> findForOwner(
            @Param("menuId") Long menuId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("minScore") Short minScore,
            Pageable pageable
    );

    @Query("""
            select r.score, count(r)
            from MenuRating r
            where r.menuId = :menuId
            group by r.score
            order by r.score
            """)
    List<Object[]> scoreHistogramByMenuId(@Param("menuId") Long menuId);

    @Query("""
            select coalesce(avg(r.score), 0.0)
            from MenuRating r
            where r.menuId in :menuIds
              and r.createdAt between :from and :to
            """)
    Double averageScoreByMenuIdInAndPeriod(
            @Param("menuIds") Collection<Long> menuIds,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            select count(r)
            from MenuRating r
            where r.menuId in :menuIds
              and r.createdAt between :from and :to
            """)
    long countByMenuIdInAndPeriod(
            @Param("menuIds") Collection<Long> menuIds,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            select r.score, count(r)
            from MenuRating r
            where r.menuId in :menuIds
              and r.createdAt between :from and :to
            group by r.score
            order by r.score
            """)
    List<Object[]> scoreHistogramByMenuIdInAndPeriod(
            @Param("menuIds") Collection<Long> menuIds,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            select r
            from MenuRating r
            where r.menuId in :menuIds
              and r.createdAt between :from and :to
              and r.comment is not null
              and r.comment <> ''
            order by r.score asc, r.createdAt desc
            """)
    List<MenuRating> sampleCommentsByMenuIdInAndPeriod(
            @Param("menuIds") Collection<Long> menuIds,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );
}
