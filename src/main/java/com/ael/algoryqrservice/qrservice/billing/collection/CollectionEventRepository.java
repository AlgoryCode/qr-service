package com.ael.algoryqrservice.qrservice.billing.collection;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CollectionEventRepository extends JpaRepository<CollectionEvent, Long> {

    List<CollectionEvent> findByCaseIdOrderByCreatedAtAscIdAsc(Long caseId);
}
