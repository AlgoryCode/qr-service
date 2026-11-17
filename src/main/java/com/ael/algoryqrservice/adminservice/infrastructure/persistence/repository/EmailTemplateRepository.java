package com.ael.algoryqrservice.adminservice.infrastructure.persistence.repository;

import com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity.EmailTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, String> {
}
