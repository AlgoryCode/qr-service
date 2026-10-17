package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.UserAccountingEntry;
import com.ael.algoryqrservice.qrservice.model.enums.AccountingSourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UserAccountingEntryRepository
        extends JpaRepository<UserAccountingEntry, Long>, JpaSpecificationExecutor<UserAccountingEntry> {

    boolean existsBySourceTypeAndSourceBillId(AccountingSourceType sourceType, Long sourceBillId);
}
