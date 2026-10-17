package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.DashboardUserSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DashboardUserSessionRepository extends JpaRepository<DashboardUserSession, UUID> {
}
