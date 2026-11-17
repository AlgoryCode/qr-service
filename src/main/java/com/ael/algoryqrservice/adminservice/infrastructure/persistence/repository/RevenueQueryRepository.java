package com.ael.algoryqrservice.adminservice.infrastructure.persistence.repository;

import com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity.AdminAuditLog;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Read-only money totals straight from the payment table. */
public interface RevenueQueryRepository extends Repository<AdminAuditLog, Long> {

    interface MonthTotal {
        String getMonth();

        BigDecimal getGross();

        BigDecimal getRefunded();

        long getPayments();
    }

    /** Collected (successful, non-verification) payments per calendar month since {@code from}. */
    @Query(value = """
            select to_char(date_trunc('month', p.created_at), 'YYYY-MM') as month,
                   coalesce(sum(coalesce(p.paid_price, p.price)), 0) as gross,
                   coalesce(sum(coalesce(p.refunded_amount, 0)), 0) as refunded,
                   count(*) as payments
            from tbl_payment p
            where p.verification_only = false
              and p.status in ('SUCCESS', 'REFUNDED')
              and p.created_at >= :from
            group by 1
            order by 1
            """, nativeQuery = true)
    List<MonthTotal> collectedByMonth(@Param("from") LocalDateTime from);
}
