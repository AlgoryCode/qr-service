package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.Menu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MenuRepository extends JpaRepository<Menu, Long> {
    Optional<Menu> findByQrIdAndDeletedFalse(Long qrId);

    Optional<Menu> findByPublicSlugIgnoreCaseAndDeletedFalse(String publicSlug);

    boolean existsByPublicSlugIgnoreCaseAndDeletedFalse(String publicSlug);

    boolean existsByPublicSlugIgnoreCaseAndDeletedFalseAndMenuIdNot(String publicSlug, Long menuId);
}
