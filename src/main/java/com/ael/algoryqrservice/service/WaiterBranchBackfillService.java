package com.ael.algoryqrservice.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class WaiterBranchBackfillService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public int backfillWaiterBranches() {
        int waiters = executeQuietly("""
                update tbl_menu_waiter w
                set branch_id = m.branch_id
                from tbl_menu m
                where w.menu_id = m.menu_id
                  and w.branch_id is null
                  and m.branch_id is not null
                """);
        int commissions = executeQuietly("""
                update tbl_waiter_commission_record r
                set branch_id = w.branch_id
                from tbl_menu_waiter w
                where r.waiter_id = w.id
                  and r.branch_id is null
                  and w.branch_id is not null
                """);
        Number missing = (Number) entityManager.createNativeQuery("""
                select count(*)
                from tbl_menu_waiter
                where branch_id is null
                """).getSingleResult();
        if (missing != null && missing.longValue() > 0) {
            log.error("Waiter branch backfill left {} waiters without branch_id", missing.longValue());
        }
        return waiters + commissions;
    }

    private int executeQuietly(String sql) {
        try {
            return entityManager.createNativeQuery(sql).executeUpdate();
        } catch (RuntimeException exception) {
            log.warn("Waiter branch backfill statement skipped: {}", exception.getMessage());
            return 0;
        }
    }
}
