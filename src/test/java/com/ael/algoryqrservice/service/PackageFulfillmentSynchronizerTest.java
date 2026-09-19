package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.model.FulfillmentDetail;
import com.ael.algoryqrservice.model.GrantFulfillment;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.enums.FulfillmentDetailSource;
import com.ael.algoryqrservice.model.enums.GrantFulfillmentStatus;
import com.ael.algoryqrservice.repository.FulfillmentDetailRepository;
import com.ael.algoryqrservice.repository.GrantFulfillmentRepository;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PackageFulfillmentSynchronizerTest {

    @Mock
    private GrantFulfillmentRepository grantFulfillmentRepository;
    @Mock
    private FulfillmentDetailRepository fulfillmentDetailRepository;
    @Mock
    private PlanPackageRepository planPackageRepository;

    @InjectMocks
    private PackageFulfillmentSynchronizer synchronizer;

    @Test
    void appendMissingProducts_whenPackageGainedOnlineOrder_thenAddsOnlyThatDetail() {
        GrantFulfillment fulfillment = GrantFulfillment.builder()
                .id(11L)
                .userId(22L)
                .packageId(4L)
                .build();
        Product qrCreate = Product.builder()
                .id(3L)
                .code(CatalogProducts.QR_CREATE)
                .scopeCode(CatalogScopes.QR_CREATE_OWNER)
                .build();
        Product onlineOrder = Product.builder()
                .id(9L)
                .code(CatalogProducts.ONLINE_ORDER)
                .scopeCode(CatalogScopes.ONLINE_ORDER_OWNER)
                .build();
        PlanPackage planPackage = PlanPackage.builder()
                .id(4L)
                .items(List.of(
                        PlanPackageItem.builder().product(qrCreate).quantity(5).unlimited(false).build(),
                        PlanPackageItem.builder().product(onlineOrder).quantity(1).unlimited(true).build()
                ))
                .build();
        FulfillmentDetail existing = FulfillmentDetail.builder()
                .fulfillmentId(11L)
                .featureCode(CatalogProducts.QR_CREATE)
                .source(FulfillmentDetailSource.PACKAGE_INCLUDE)
                .usedQuantity(2)
                .build();

        when(grantFulfillmentRepository.findByUserIdAndStatus(22L, GrantFulfillmentStatus.ACTIVE))
                .thenReturn(List.of(fulfillment));
        when(fulfillmentDetailRepository.findByFulfillmentId(11L)).thenReturn(List.of(existing));
        when(planPackageRepository.findByIdWithItems(4L)).thenReturn(Optional.of(planPackage));

        int added = synchronizer.appendMissingProducts(22L);

        assertThat(added).isEqualTo(1);
        ArgumentCaptor<FulfillmentDetail> captor = ArgumentCaptor.forClass(FulfillmentDetail.class);
        verify(fulfillmentDetailRepository).save(captor.capture());
        assertThat(captor.getValue().getFeatureCode()).isEqualTo(CatalogProducts.ONLINE_ORDER);
        assertThat(captor.getValue().getScopeCode()).isEqualTo(CatalogScopes.ONLINE_ORDER_OWNER);
        assertThat(captor.getValue().getSource()).isEqualTo(FulfillmentDetailSource.PACKAGE_INCLUDE);
        assertThat(captor.getValue().isUnlimited()).isTrue();
        assertThat(captor.getValue().getUsedQuantity()).isZero();
    }

    @Test
    void appendMissingProducts_whenAllPackageItemsExist_thenDoesNotSave() {
        GrantFulfillment fulfillment = GrantFulfillment.builder().id(11L).userId(22L).packageId(4L).build();
        Product qrCreate = Product.builder()
                .id(3L)
                .code(CatalogProducts.QR_CREATE)
                .scopeCode(CatalogScopes.QR_CREATE_OWNER)
                .build();
        PlanPackage planPackage = PlanPackage.builder()
                .id(4L)
                .items(List.of(PlanPackageItem.builder().product(qrCreate).quantity(5).unlimited(false).build()))
                .build();

        when(grantFulfillmentRepository.findByUserIdAndStatus(22L, GrantFulfillmentStatus.ACTIVE))
                .thenReturn(List.of(fulfillment));
        when(fulfillmentDetailRepository.findByFulfillmentId(11L)).thenReturn(List.of(
                FulfillmentDetail.builder()
                        .featureCode(CatalogProducts.QR_CREATE)
                        .source(FulfillmentDetailSource.PACKAGE_INCLUDE)
                        .build()
        ));
        when(planPackageRepository.findByIdWithItems(4L)).thenReturn(Optional.of(planPackage));

        int added = synchronizer.appendMissingProducts(22L);

        assertThat(added).isZero();
        verify(fulfillmentDetailRepository, never()).save(any());
    }

    @Test
    void appendMissingProducts_whenAddonFulfillment_thenSkips() {
        GrantFulfillment fulfillment = GrantFulfillment.builder().id(11L).userId(22L).packageId(4L).build();

        when(grantFulfillmentRepository.findByUserIdAndStatus(22L, GrantFulfillmentStatus.ACTIVE))
                .thenReturn(List.of(fulfillment));
        when(fulfillmentDetailRepository.findByFulfillmentId(11L)).thenReturn(List.of(
                FulfillmentDetail.builder()
                        .featureCode(CatalogProducts.QR_MENU_ADDON)
                        .source(FulfillmentDetailSource.ADDON_PURCHASE)
                        .build()
        ));

        int added = synchronizer.appendMissingProducts(22L);

        assertThat(added).isZero();
        verify(planPackageRepository, never()).findByIdWithItems(any());
        verify(fulfillmentDetailRepository, never()).save(any());
    }
}
