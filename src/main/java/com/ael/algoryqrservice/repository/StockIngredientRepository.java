package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.StockIngredient;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StockIngredientRepository extends JpaRepository<StockIngredient, Long> {

    Optional<StockIngredient> findByIdAndUserIdAndDeletedFalse(Long id, Long userId);

    @Query(
            value = """
                    select i from StockIngredient i
                    where i.branchId = :branchId
                      and i.userId = :userId
                      and i.deleted = false
                      and (:lowOnly = false or i.quantity <= i.minQuantity)
                    """,
            countQuery = """
                    select count(i) from StockIngredient i
                    where i.branchId = :branchId
                      and i.userId = :userId
                      and i.deleted = false
                      and (:lowOnly = false or i.quantity <= i.minQuantity)
                    """
    )
    Page<StockIngredient> search(
            @Param("branchId") Long branchId,
            @Param("userId") Long userId,
            @Param("lowOnly") boolean lowOnly,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from StockIngredient i where i.id in :ids")
    List<StockIngredient> lockByIds(@Param("ids") Collection<Long> ids);
}
