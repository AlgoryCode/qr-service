package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.exception.ForbiddenException;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.dto.ConsumedEntitlement;
import com.ael.algoryqrservice.model.dto.FulfillmentConsumeResult;
import com.ael.algoryqrservice.model.enums.FulfillmentReferenceType;
import com.ael.algoryqrservice.repository.ProductRepository;
import com.ael.algoryqrservice.service.entitlement.EntitlementMaintenanceService;
import com.ael.algoryqrservice.service.entitlement.FeatureUsageSyncRegistry;
import com.ael.algoryqrservice.service.entitlement.PurchaseExpiryService;
import com.ael.algoryqrservice.service.entitlement.UserEntitlementQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntitlementServiceTest {

    private static final Long USER_ID = 7L;

    @Mock
    private ProductRepository productRepository;
    @Mock
    private FulfillmentGateService fulfillmentGateService;
    @Mock
    private PurchaseExpiryService purchaseExpiryService;
    @Mock
    private EntitlementMaintenanceService maintenanceService;
    @Mock
    private FeatureUsageSyncRegistry usageSyncRegistry;
    @Mock
    private UserEntitlementQueryService entitlementQueryService;

    @InjectMocks
    private EntitlementService entitlementService;

    @Test
    void hasScope_whenGateAllows_thenReturnTrueWithoutRepair() {
        when(fulfillmentGateService.hasScope(USER_ID, CatalogScopes.QR_MENU_OWNER)).thenReturn(true);

        assertThat(entitlementService.hasScope(USER_ID, CatalogScopes.QR_MENU_OWNER)).isTrue();

        verify(maintenanceService, never()).repairUser(USER_ID);
        verify(purchaseExpiryService, never()).expireDueForUser(USER_ID);
    }

    @Test
    void requireScope_whenGateDenies_thenThrowForbidden() {
        when(fulfillmentGateService.hasScope(USER_ID, CatalogScopes.QR_MENU_OWNER)).thenReturn(false);

        assertThatThrownBy(() -> entitlementService.requireScope(USER_ID, CatalogScopes.QR_MENU_OWNER))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining(CatalogScopes.QR_MENU_OWNER);

        verify(purchaseExpiryService).expireDueForUser(USER_ID);
        verify(maintenanceService).repairUser(USER_ID);
    }

    @Test
    void consume_whenQuotaAvailable_thenReturnConsumedEntitlement() {
        when(productRepository.findByCode(CatalogProducts.QR_CREATE)).thenReturn(Optional.of(
                Product.builder().id(3L).code(CatalogProducts.QR_CREATE).consumable(true).build()
        ));
        when(fulfillmentGateService.consumeFeature(
                USER_ID, CatalogProducts.QR_CREATE, 1, FulfillmentReferenceType.FEATURE, null
        )).thenReturn(new FulfillmentConsumeResult(1, 10L, 21L));

        ConsumedEntitlement consumed = entitlementService.consume(USER_ID, CatalogProducts.QR_CREATE, 1);

        assertThat(consumed).isEqualTo(new ConsumedEntitlement(10L, 21L, 1));
        verify(maintenanceService).backfillFulfillment(USER_ID);
    }

    @Test
    void consume_whenProductRequiresCountSync_thenSynchronizeUsageFirst() {
        when(productRepository.findByCode(CatalogProducts.QR_MENU)).thenReturn(Optional.of(
                Product.builder()
                        .id(3L)
                        .code(CatalogProducts.QR_MENU)
                        .consumable(true)
                        .requiresCountSync(true)
                        .build()
        ));
        when(fulfillmentGateService.consumeFeature(
                USER_ID, CatalogProducts.QR_MENU, 1, FulfillmentReferenceType.FEATURE, null
        )).thenReturn(new FulfillmentConsumeResult(1, 10L, 21L));

        entitlementService.consume(USER_ID, CatalogProducts.QR_MENU, 1);

        verify(usageSyncRegistry).synchronize(USER_ID, CatalogProducts.QR_MENU);
    }

    @Test
    void consume_whenQuotaExhausted_thenThrowFeatureSpecificMessage() {
        when(productRepository.findByCode(CatalogProducts.QR_MENU)).thenReturn(Optional.of(
                Product.builder().id(3L).code(CatalogProducts.QR_MENU).consumable(true).build()
        ));
        when(fulfillmentGateService.consumeFeature(
                USER_ID, CatalogProducts.QR_MENU, 1, FulfillmentReferenceType.FEATURE, null
        )).thenReturn(new FulfillmentConsumeResult(0, null, null));

        assertThatThrownBy(() -> entitlementService.consume(USER_ID, CatalogProducts.QR_MENU, 1))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("dijital menü");
    }

    @Test
    void consume_whenProductIsNotConsumable_thenOnlyRequireScope() {
        when(productRepository.findByCode(CatalogProducts.SMART_REPORTING)).thenReturn(Optional.of(
                Product.builder()
                        .id(5L)
                        .code(CatalogProducts.SMART_REPORTING)
                        .scopeCode(CatalogScopes.SMART_REPORTING_OWNER)
                        .consumable(false)
                        .build()
        ));
        when(fulfillmentGateService.hasScope(USER_ID, CatalogScopes.SMART_REPORTING_OWNER)).thenReturn(true);

        assertThat(entitlementService.consume(USER_ID, CatalogProducts.SMART_REPORTING, 1)).isNull();

        verify(fulfillmentGateService, never()).consumeFeature(
                USER_ID, CatalogProducts.SMART_REPORTING, 1, FulfillmentReferenceType.FEATURE, null
        );
    }

    @Test
    void consume_whenProductHasFeatureCode_thenConsumeAgainstFeatureCode() {
        when(productRepository.findByCode(CatalogProducts.QR_MENU_ADDON)).thenReturn(Optional.of(
                Product.builder()
                        .id(4L)
                        .code(CatalogProducts.QR_MENU_ADDON)
                        .featureCode(CatalogProducts.QR_MENU)
                        .consumable(true)
                        .build()
        ));
        when(fulfillmentGateService.consumeFeature(
                USER_ID, CatalogProducts.QR_MENU, 1, FulfillmentReferenceType.FEATURE, null
        )).thenReturn(new FulfillmentConsumeResult(1, 10L, 21L));

        entitlementService.consume(USER_ID, CatalogProducts.QR_MENU_ADDON, 1);

        verify(fulfillmentGateService).consumeFeature(
                USER_ID, CatalogProducts.QR_MENU, 1, FulfillmentReferenceType.FEATURE, null
        );
    }

    @Test
    void release_whenConsumableProduct_thenReleaseOnGate() {
        when(productRepository.findByCode(CatalogProducts.QR_MENU)).thenReturn(Optional.of(
                Product.builder().id(3L).code(CatalogProducts.QR_MENU).consumable(true).build()
        ));

        entitlementService.release(USER_ID, CatalogProducts.QR_MENU, 1);

        verify(maintenanceService).backfillFulfillment(USER_ID);
        verify(fulfillmentGateService).releaseFeature(
                USER_ID, CatalogProducts.QR_MENU, 1, FulfillmentReferenceType.FEATURE, null
        );
    }

    @Test
    void release_whenAmountIsNotPositive_thenDoNothing() {
        entitlementService.release(USER_ID, CatalogProducts.QR_MENU, 0);

        verify(purchaseExpiryService, never()).expireDueForUser(USER_ID);
        verify(productRepository, never()).findByCode(CatalogProducts.QR_MENU);
    }

    @Test
    void assertMenuProductCreationAllowed_whenRemainingCoversRequest_thenPass() {
        when(fulfillmentGateService.remainingQuantity(USER_ID, CatalogProducts.MENU_PRODUCT, false))
                .thenReturn(Integer.MAX_VALUE);

        assertThatCode(() -> entitlementService.assertMenuProductCreationAllowed(USER_ID, 1))
                .doesNotThrowAnyException();

        verify(maintenanceService).repairUser(USER_ID);
    }

    @Test
    void assertMenuProductCreationAllowed_whenRemainingIsTooLow_thenThrowForbidden() {
        when(fulfillmentGateService.remainingQuantity(USER_ID, CatalogProducts.MENU_PRODUCT, false))
                .thenReturn(1);

        assertThatThrownBy(() -> entitlementService.assertMenuProductCreationAllowed(USER_ID, 2))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void assertMenuProductCreationAllowed_whenNoAdditionalProducts_thenSkipRepair() {
        assertThatCode(() -> entitlementService.assertMenuProductCreationAllowed(USER_ID, 0))
                .doesNotThrowAnyException();

        verify(maintenanceService, never()).repairUser(USER_ID);
    }

    @Test
    void assertMenuProductCreationAllowed_whenUserIsNull_thenThrowForbidden() {
        assertThatThrownBy(() -> entitlementService.assertMenuProductCreationAllowed(null, 1))
                .isInstanceOf(ForbiddenException.class);
    }
}
