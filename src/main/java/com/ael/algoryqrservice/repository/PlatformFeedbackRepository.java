package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.PlatformFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PlatformFeedbackRepository extends JpaRepository<PlatformFeedback, Long>, JpaSpecificationExecutor<PlatformFeedback> {
}
