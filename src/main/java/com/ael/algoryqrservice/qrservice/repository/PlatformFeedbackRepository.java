package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.PlatformFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PlatformFeedbackRepository extends JpaRepository<PlatformFeedback, Long>, JpaSpecificationExecutor<PlatformFeedback> {
}
