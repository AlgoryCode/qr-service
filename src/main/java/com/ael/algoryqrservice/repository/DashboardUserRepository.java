package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.DashboardUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DashboardUserRepository extends JpaRepository<DashboardUser, Long> {

    Optional<DashboardUser> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
