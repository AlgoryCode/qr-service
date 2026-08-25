package com.ael.algoryqrservice.service.entitlement;

import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.QrRepository;
import com.ael.algoryqrservice.service.fulfillment.FulfillmentLedger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.ael.algoryqrservice.model.enums.FulfillmentDetailSource.ADDON_PURCHASE;
import static com.ael.algoryqrservice.model.enums.FulfillmentDetailSource.PACKAGE_INCLUDE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeatureUsageSynchronizerTest {

    private static final Long USER_ID = 7L;

    @Mock
    private QrRepository qrRepository;
    @Mock
    private MenuRepository menuRepository;
    @Mock
    private MenuProductRepository menuProductRepository;
    @Mock
    private FulfillmentLedger ledger;

    @Test
    void qrCreateSynchronize_whenFiveActiveQrs_thenReplaceUsedQuantityWithFive() {
        when(qrRepository.countByUserIdAndDeletedFalse(USER_ID)).thenReturn(5L);

        new QrCreateUsageSynchronizer(qrRepository, ledger).synchronize(USER_ID);

        verify(ledger).replaceUsedQuantity(
                USER_ID, CatalogProducts.QR_CREATE, 5, List.of(PACKAGE_INCLUDE, ADDON_PURCHASE)
        );
    }

    @Test
    void menuProductSynchronize_whenTwelveActiveProducts_thenReplaceUsedQuantityWithTwelve() {
        when(menuProductRepository.countActiveProductsForUser(USER_ID)).thenReturn(12L);

        new MenuProductUsageSynchronizer(menuProductRepository, ledger).synchronize(USER_ID);

        verify(ledger).replaceUsedQuantity(
                USER_ID, CatalogProducts.MENU_PRODUCT, 12, List.of(PACKAGE_INCLUDE, ADDON_PURCHASE)
        );
    }

    @Test
    void qrMenuSynchronize_whenBranchesHoldExtraMenus_thenChargeOnlyMenusBeyondTheFirst() {
        when(menuRepository.countActiveLiveMenusGroupedByBranch(USER_ID)).thenReturn(List.of(
                new Object[]{1L, 3L},
                new Object[]{2L, 1L},
                new Object[]{3L, 2L}
        ));

        new QrMenuUsageSynchronizer(new ExtraMenuQuotaCalculator(menuRepository), ledger).synchronize(USER_ID);

        verify(ledger).replaceUsedQuantity(USER_ID, CatalogProducts.QR_MENU, 3, List.of(ADDON_PURCHASE));
    }

    @Test
    void countExtraMenus_whenUserIsNull_thenReturnZero() {
        assertThat(new ExtraMenuQuotaCalculator(menuRepository).countExtraMenus(null)).isZero();
    }
}
