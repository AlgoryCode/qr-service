package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.StockMovement;
import com.ael.algoryqrservice.model.enums.StockMovementKind;
import com.ael.algoryqrservice.model.enums.StockSourceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    Page<StockMovement> findByIngredientIdOrderByCreatedAtDescIdDesc(Long ingredientId, Pageable pageable);

    boolean existsByIngredientIdAndKindAndSourceTypeAndSourceId(
            Long ingredientId,
            StockMovementKind kind,
            StockSourceType sourceType,
            Long sourceId
    );

    List<StockMovement> findBySourceTypeAndSourceIdAndKind(
            StockSourceType sourceType,
            Long sourceId,
            StockMovementKind kind
    );
}
