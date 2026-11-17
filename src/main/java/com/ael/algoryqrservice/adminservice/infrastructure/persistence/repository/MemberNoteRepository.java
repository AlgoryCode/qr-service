package com.ael.algoryqrservice.adminservice.infrastructure.persistence.repository;

import com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity.MemberNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemberNoteRepository extends JpaRepository<MemberNote, Long> {
    List<MemberNote> findByMerchantIdOrderByPinnedDescCreatedAtDesc(Long merchantId);
}
