package com.ael.algoryqrservice.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseSchemaCompatibilityFixerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private PurchaseSchemaCompatibilityFixer fixer;

    @Test
    void run_whenPurchaseTypeConstraintAlreadyAllowsAddon_thenSkipUpdate() {
        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), eq("tbl_purchase_purchase_type_check")))
                .thenReturn("CHECK ((purchase_type)::text = ANY (ARRAY['ADD_ON'::text]))");

        fixer.run(new DefaultApplicationArguments(new String[0]));

        verify(jdbcTemplate, never()).execute("ALTER TABLE tbl_purchase DROP CONSTRAINT IF EXISTS tbl_purchase_purchase_type_check");
    }

    @Test
    void run_whenPurchaseTypeConstraintMissingAddon_thenRecreateConstraint() {
        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), eq("tbl_purchase_purchase_type_check")))
                .thenReturn("CHECK ((purchase_type)::text = ANY (ARRAY['PAID'::text]))");

        fixer.run(new DefaultApplicationArguments(new String[0]));

        verify(jdbcTemplate).execute("ALTER TABLE tbl_purchase DROP CONSTRAINT IF EXISTS tbl_purchase_purchase_type_check");
        verify(jdbcTemplate).execute(
                "ALTER TABLE tbl_purchase ADD CONSTRAINT tbl_purchase_purchase_type_check "
                        + "CHECK (purchase_type IN ('FREE', 'TRIAL', 'PAID', 'SYSTEM_GRANT', 'ADD_ON'))"
        );
    }
}
