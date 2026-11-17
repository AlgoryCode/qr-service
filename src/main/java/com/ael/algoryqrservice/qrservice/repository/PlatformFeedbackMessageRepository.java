package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.PlatformFeedbackMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlatformFeedbackMessageRepository extends JpaRepository<PlatformFeedbackMessage, Long> {

    List<PlatformFeedbackMessage> findByFeedbackIdOrderByCreatedAtAsc(Long feedbackId);
}
