package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.FaqEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FaqEntryRepository extends JpaRepository<FaqEntry, Long> {

    List<FaqEntry> findAllByOrderByCategoryAscSortOrderAscIdAsc();

    List<FaqEntry> findByPublishedTrueOrderByCategoryAscSortOrderAscIdAsc();
}
