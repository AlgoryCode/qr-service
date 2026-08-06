package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.MenuProductRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MenuProductRatingRepository extends JpaRepository<MenuProductRating, Long> {

    Optional<MenuProductRating> findByMenuProductIdAndIpAddress(Long menuProductId, String ipAddress);

    long countByMenuProductId(Long menuProductId);

    @Query("""
            select coalesce(avg(r.score), 0.0)
            from MenuProductRating r
            where r.menuProductId = :productId
            """)
    Double averageScoreByProductId(@Param("productId") Long productId);
}
