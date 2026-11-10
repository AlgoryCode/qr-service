package com.ael.algoryqrservice.qrservice.billing.service;

import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.model.enums.AccountingEntryType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserAccountingTypeFilterTest {

    @Test
    void allOrEmptyMeansEveryType() {
        assertThat(UserAccountingService.parseTypeFilter(null)).isEmpty();
        assertThat(UserAccountingService.parseTypeFilter("all")).isEmpty();
        assertThat(UserAccountingService.parseTypeFilter(" ")).isEmpty();
    }

    @Test
    void readsOneOrSeveralTypes() {
        assertThat(UserAccountingService.parseTypeFilter("gelir")).containsExactly(AccountingEntryType.GELIR);
        assertThat(UserAccountingService.parseTypeFilter("GELIR,GIDER"))
                .containsExactlyInAnyOrder(AccountingEntryType.GELIR, AccountingEntryType.GIDER);
    }

    @Test
    void rejectsAnUnknownType() {
        assertThatThrownBy(() -> UserAccountingService.parseTypeFilter("GELIR,KASA")).isInstanceOf(BadRequestException.class);
    }
}
