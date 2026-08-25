package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.model.dto.FulfillmentConsumeResult;
import com.ael.algoryqrservice.model.enums.FulfillmentDetailSource;
import com.ael.algoryqrservice.model.enums.FulfillmentReferenceType;
import com.ael.algoryqrservice.service.fulfillment.FulfillmentLedger;
import com.ael.algoryqrservice.service.fulfillment.FulfillmentQuotaStrategy;
import com.ael.algoryqrservice.service.fulfillment.FulfillmentQuotaStrategyFactory;
import com.ael.algoryqrservice.service.fulfillment.UsageReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FulfillmentGateServiceTest {

    private static final Long USER_ID = 22L;

    @Mock
    private FulfillmentQuotaStrategyFactory strategyFactory;
    @Mock
    private FulfillmentQuotaStrategy strategy;
    @Mock
    private FulfillmentLedger ledger;

    @InjectMocks
    private FulfillmentGateService fulfillmentGateService;

    @Test
    void hasScope_whenStrategyGrantsScope_thenAllow() {
        when(strategyFactory.strategyFor(USER_ID)).thenReturn(strategy);
        when(strategy.hasScope(USER_ID, CatalogScopes.QR_CREATE_OWNER)).thenReturn(true);

        assertThat(fulfillmentGateService.hasScope(USER_ID, CatalogScopes.QR_CREATE_OWNER)).isTrue();
    }

    @Test
    void hasScope_whenStrategyDeniesScope_thenDeny() {
        when(strategyFactory.strategyFor(USER_ID)).thenReturn(strategy);
        when(strategy.hasScope(USER_ID, CatalogScopes.QR_CREATE_OWNER)).thenReturn(false);

        assertThat(fulfillmentGateService.hasScope(USER_ID, CatalogScopes.QR_CREATE_OWNER)).isFalse();
    }

    @Test
    void consumeFeature_whenLedgerBacked_thenBookPackageBeforeAddon() {
        when(strategyFactory.strategyFor(USER_ID)).thenReturn(strategy);
        when(strategy.supportsLedger()).thenReturn(true);
        when(ledger.consume(
                USER_ID,
                CatalogProducts.QR_CREATE,
                1,
                List.of(FulfillmentDetailSource.PACKAGE_INCLUDE, FulfillmentDetailSource.ADDON_PURCHASE),
                UsageReference.of(FulfillmentReferenceType.QR, 34L)
        )).thenReturn(new FulfillmentConsumeResult(1, 333L, 21L));

        FulfillmentConsumeResult result = fulfillmentGateService.consumeFeature(
                USER_ID, CatalogProducts.QR_CREATE, 1, FulfillmentReferenceType.QR, 34L
        );

        assertThat(result.fullyConsumed(1)).isTrue();
        assertThat(result.purchaseId()).isEqualTo(333L);
        assertThat(result.detailId()).isEqualTo(21L);
    }

    @Test
    void consumeAddon_whenLedgerBacked_thenBookAddonOnly() {
        when(strategyFactory.strategyFor(USER_ID)).thenReturn(strategy);
        when(strategy.supportsLedger()).thenReturn(true);
        when(ledger.consume(
                USER_ID,
                CatalogProducts.QR_MENU,
                1,
                List.of(FulfillmentDetailSource.ADDON_PURCHASE),
                UsageReference.of(FulfillmentReferenceType.MENU, 3L)
        )).thenReturn(new FulfillmentConsumeResult(1, 333L, 21L));

        FulfillmentConsumeResult result = fulfillmentGateService.consumeAddon(
                USER_ID, CatalogProducts.QR_MENU, 1, FulfillmentReferenceType.MENU, 3L
        );

        assertThat(result.fullyConsumed(1)).isTrue();
    }

    @Test
    void consumeFeature_whenStrategyHasNoLedger_thenSkipLedgerAndReportNothingConsumed() {
        when(strategyFactory.strategyFor(USER_ID)).thenReturn(strategy);
        when(strategy.supportsLedger()).thenReturn(false);

        FulfillmentConsumeResult result = fulfillmentGateService.consumeFeature(
                USER_ID, CatalogProducts.QR_CREATE, 1, FulfillmentReferenceType.QR, 34L
        );

        assertThat(result.fullyConsumed(1)).isFalse();
        verifyNoInteractions(ledger);
    }

    @Test
    void consumeFeature_whenAmountIsNotPositive_thenSkipLedger() {
        FulfillmentConsumeResult result = fulfillmentGateService.consumeFeature(
                USER_ID, CatalogProducts.QR_CREATE, 0, FulfillmentReferenceType.QR, 34L
        );

        assertThat(result.consumed()).isZero();
        verifyNoInteractions(ledger);
    }

    @Test
    void releaseFeature_whenLedgerBacked_thenGiveAddonBackBeforePackage() {
        when(strategyFactory.strategyFor(USER_ID)).thenReturn(strategy);
        when(strategy.supportsLedger()).thenReturn(true);

        fulfillmentGateService.releaseFeature(
                USER_ID, CatalogProducts.QR_MENU, 2, FulfillmentReferenceType.MENU, 3L
        );

        verify(ledger).release(
                USER_ID,
                CatalogProducts.QR_MENU,
                2,
                List.of(FulfillmentDetailSource.ADDON_PURCHASE, FulfillmentDetailSource.PACKAGE_INCLUDE),
                UsageReference.of(FulfillmentReferenceType.MENU, 3L)
        );
    }

    @Test
    void releaseAddon_whenStrategyHasNoLedger_thenSkipLedger() {
        when(strategyFactory.strategyFor(USER_ID)).thenReturn(strategy);
        when(strategy.supportsLedger()).thenReturn(false);

        fulfillmentGateService.releaseAddon(
                USER_ID, CatalogProducts.QR_MENU, 1, FulfillmentReferenceType.MENU, 3L
        );

        verify(ledger, never()).release(any(), anyString(), anyInt(), any(), any());
    }

    @Test
    void remainingQuantity_whenAskedForFeature_thenDelegateToLedger() {
        when(ledger.remainingQuantity(USER_ID, CatalogProducts.MENU_PRODUCT, false)).thenReturn(4);

        assertThat(fulfillmentGateService.remainingQuantity(USER_ID, CatalogProducts.MENU_PRODUCT, false))
                .isEqualTo(4);
    }

    @Test
    void logFeatureUsage_whenCalled_thenDelegateToLedger() {
        fulfillmentGateService.logFeatureUsage(
                USER_ID, CatalogProducts.SMART_REPORTING, FulfillmentReferenceType.FEATURE, 10L
        );

        verify(ledger).logUsage(
                USER_ID,
                CatalogProducts.SMART_REPORTING,
                UsageReference.of(FulfillmentReferenceType.FEATURE, 10L)
        );
    }
}
