package com.ael.algoryqrservice.adminservice.infrastructure.persistence.repository;

import com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity.DashboardUserSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DashboardUserSessionRepository extends JpaRepository<DashboardUserSession, UUID> {
}
