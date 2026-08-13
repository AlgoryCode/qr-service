package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.MenuProductRating;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MenuProductRatingRepository extends JpaRepository<MenuProductRating, Long> {

    Optional<MenuProductRating> findByMenuProductIdAndIpAddress(Long menuProductId, String ipAddress);

    long countByMenuProductId(Long menuProductId);

    long countByMenuId(Long menuId);

    @Query("""
            select coalesce(avg(r.score), 0.0)
            from MenuProductRating r
            where r.menuProductId = :productId
            """)
    Double averageScoreByProductId(@Param("productId") Long productId);

    @Query("""
            select coalesce(avg(r.score), 0.0)
            from MenuProductRating r
            where r.menuId = :menuId
            """)
    Double averageScoreByMenuId(@Param("menuId") Long menuId);

    @Query("""
            select coalesce(avg(r.score), 0.0)
            from MenuProductRating r
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
            from MenuProductRating r
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
            from MenuProductRating r
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
            select r.score, count(r)
            from MenuProductRating r
            where r.menuId = :menuId
            group by r.score
            order by r.score
            """)
    List<Object[]> scoreHistogramByMenuId(@Param("menuId") Long menuId);

    @Query("""
            select r.menuProductId, coalesce(avg(r.score), 0.0), count(r)
            from MenuProductRating r
            where r.menuId = :menuId
              and r.createdAt between :from and :to
            group by r.menuProductId
            having count(r) >= :minCount
            order by avg(r.score) desc, count(r) desc
            """)
    List<Object[]> topRatedProductsByPeriod(
            @Param("menuId") Long menuId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("minCount") long minCount,
            Pageable pageable
    );

    @Query("""
            select r.menuProductId, coalesce(avg(r.score), 0.0), count(r)
            from MenuProductRating r
            where r.menuId = :menuId
              and r.createdAt between :from and :to
            group by r.menuProductId
            having count(r) >= :minCount
            order by avg(r.score) asc, count(r) desc
            """)
    List<Object[]> bottomRatedProductsByPeriod(
            @Param("menuId") Long menuId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("minCount") long minCount,
            Pageable pageable
    );

    @Query("""
            select r
            from MenuProductRating r
            where r.menuId = :menuId
              and r.createdAt between :from and :to
              and r.comment is not null
              and r.comment <> ''
            order by r.score asc, r.createdAt desc
            """)
    List<MenuProductRating> sampleCommentsByMenuIdAndPeriod(
            @Param("menuId") Long menuId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );

    @Query("""
            select r
            from MenuProductRating r
            where r.menuId = :menuId
              and (:from is null or r.createdAt >= :from)
              and (:to is null or r.createdAt <= :to)
              and (:minScore is null or r.score >= :minScore)
            order by r.createdAt desc
            """)
    Page<MenuProductRating> findForOwner(
            @Param("menuId") Long menuId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("minScore") Short minScore,
            Pageable pageable
    );
}
