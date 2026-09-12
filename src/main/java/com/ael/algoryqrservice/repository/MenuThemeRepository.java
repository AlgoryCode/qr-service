package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.MenuTheme;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MenuThemeRepository extends JpaRepository<MenuTheme, Long> {

    Optional<MenuTheme> findByCodeIgnoreCase(String code);

    List<MenuTheme> findAllByActiveTrueOrderBySortOrderAscIdAsc();

    List<MenuTheme> findAllByOrderBySortOrderAscIdAsc();

    List<MenuTheme> findByIdInAndActiveTrueOrderBySortOrderAscIdAsc(Collection<Long> ids);

    List<MenuTheme> findByCodeIn(Collection<String> codes);

    boolean existsByCodeIgnoreCase(String code);
}
