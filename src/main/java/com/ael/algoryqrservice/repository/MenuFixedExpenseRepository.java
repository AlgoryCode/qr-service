package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.MenuFixedExpense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MenuFixedExpenseRepository extends JpaRepository<MenuFixedExpense, Long> {

    List<MenuFixedExpense> findByMenuIdOrderByTitleAsc(Long menuId);

    List<MenuFixedExpense> findByMenuIdAndActiveTrueOrderByTitleAsc(Long menuId);
}
