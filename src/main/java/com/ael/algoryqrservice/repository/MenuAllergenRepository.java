package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.MenuAllergen;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MenuAllergenRepository extends JpaRepository<MenuAllergen, Long> {
    List<MenuAllergen> findByDeletedFalseOrderBySortOrderAscIdAsc();

    Optional<MenuAllergen> findByIdAndDeletedFalse(Long id);

    Optional<MenuAllergen> findBySlugAndDeletedFalse(String slug);

    List<MenuAllergen> findByIdInAndDeletedFalse(Collection<Long> ids);

    boolean existsBySlugAndDeletedFalse(String slug);
}
