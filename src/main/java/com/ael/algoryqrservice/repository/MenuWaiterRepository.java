package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.MenuWaiter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MenuWaiterRepository extends JpaRepository<MenuWaiter, Long> {

    Optional<MenuWaiter> findByUsernameIgnoreCase(String username);

    List<MenuWaiter> findByBranchIdOrderByDisplayNameAsc(Long branchId);

    List<MenuWaiter> findByBranchIdInOrderByDisplayNameAsc(Collection<Long> branchIds);

    Optional<MenuWaiter> findByIdAndBranchId(Long id, Long branchId);

    boolean existsByUsernameIgnoreCase(String username);
}
