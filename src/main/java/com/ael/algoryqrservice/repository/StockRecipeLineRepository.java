package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.StockRecipeLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface StockRecipeLineRepository extends JpaRepository<StockRecipeLine, Long> {

    List<StockRecipeLine> findByProductIdOrderByIdAsc(Long productId);

    List<StockRecipeLine> findByProductIdIn(Collection<Long> productIds);

    void deleteByProductId(Long productId);

    void deleteByIngredientId(Long ingredientId);
}
