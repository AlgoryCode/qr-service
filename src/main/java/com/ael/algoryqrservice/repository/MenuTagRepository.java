package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.MenuTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MenuTagRepository extends JpaRepository<MenuTag, Long> {
    List<MenuTag> findByDeletedFalseOrderBySortOrderAscIdAsc();

    Optional<MenuTag> findByIdAndDeletedFalse(Long id);

    Optional<MenuTag> findBySlugAndDeletedFalse(String slug);

    List<MenuTag> findByIdInAndDeletedFalse(Collection<Long> ids);

    boolean existsBySlugAndDeletedFalse(String slug);
}
