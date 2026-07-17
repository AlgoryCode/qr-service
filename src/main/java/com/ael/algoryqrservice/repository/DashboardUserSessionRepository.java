package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.DashboardUserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DashboardUserSessionRepository extends JpaRepository<DashboardUserSession, UUID> {

    Optional<DashboardUserSession> findByIdAndDashboardUserId(UUID id, Long dashboardUserId);

    List<DashboardUserSession> findByDashboardUserIdOrderByLoggedInAtDesc(Long dashboardUserId);
}
