package com.ael.algoryqrservice.service.entitlement;

import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.util.WritableTransactionGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeatureUsageSyncRegistryTest {

    private static final Long USER_ID = 7L;

    @Mock
    private FeatureUsageSynchronizer qrMenuSynchronizer;
    @Mock
    private FeatureUsageSynchronizer qrCreateSynchronizer;
    @Mock
    private WritableTransactionGuard writableTransactionGuard;

    @Test
    void synchronize_whenFeatureHasSynchronizer_thenRecomputeThatFeatureOnly() {
        FeatureUsageSyncRegistry registry = registry();
        when(writableTransactionGuard.allowsWrites(any(), eq(USER_ID))).thenReturn(true);

        registry.synchronize(USER_ID, CatalogProducts.QR_MENU);

        verify(qrMenuSynchronizer).synchronize(USER_ID);
        verify(qrCreateSynchronizer, never()).synchronize(any());
    }

    @Test
    void synchronize_whenFeatureIsUnknown_thenDoNothing() {
        FeatureUsageSyncRegistry registry = registry();
        when(writableTransactionGuard.allowsWrites(any(), eq(USER_ID))).thenReturn(true);

        registry.synchronize(USER_ID, "UNKNOWN_FEATURE");

        verify(qrMenuSynchronizer, never()).synchronize(any());
        verify(qrCreateSynchronizer, never()).synchronize(any());
    }

    @Test
    void synchronize_whenTransactionIsReadOnly_thenSkipWrites() {
        FeatureUsageSyncRegistry registry = registry();
        when(writableTransactionGuard.allowsWrites(any(), eq(USER_ID))).thenReturn(false);

        registry.synchronize(USER_ID, CatalogProducts.QR_MENU);

        verify(qrMenuSynchronizer, never()).synchronize(any());
    }

    @Test
    void synchronize_whenUserIsNull_thenSkipGuardAndWrites() {
        FeatureUsageSyncRegistry registry = registry();

        registry.synchronize(null, CatalogProducts.QR_MENU);

        verifyNoInteractions(writableTransactionGuard);
        verify(qrMenuSynchronizer, never()).synchronize(any());
    }

    @Test
    void synchronize_whenFeatureCodeIsNull_thenSkipGuardAndWrites() {
        FeatureUsageSyncRegistry registry = registry();

        registry.synchronize(USER_ID, null);

        verifyNoInteractions(writableTransactionGuard);
        verify(qrMenuSynchronizer, never()).synchronize(any());
    }

    @Test
    void synchronizeAll_whenWritesAllowed_thenRecomputeEveryFeature() {
        FeatureUsageSyncRegistry registry = registry();
        when(writableTransactionGuard.allowsWrites(any(), eq(USER_ID))).thenReturn(true);

        registry.synchronizeAll(USER_ID);

        verify(qrMenuSynchronizer).synchronize(USER_ID);
        verify(qrCreateSynchronizer).synchronize(USER_ID);
    }

    @Test
    void synchronizeAll_whenTransactionIsReadOnly_thenSkipEveryFeature() {
        FeatureUsageSyncRegistry registry = registry();
        when(writableTransactionGuard.allowsWrites(any(), eq(USER_ID))).thenReturn(false);

        registry.synchronizeAll(USER_ID);

        verify(qrMenuSynchronizer, never()).synchronize(any());
        verify(qrCreateSynchronizer, never()).synchronize(any());
    }

    private FeatureUsageSyncRegistry registry() {
        when(qrMenuSynchronizer.featureCode()).thenReturn(CatalogProducts.QR_MENU);
        when(qrCreateSynchronizer.featureCode()).thenReturn(CatalogProducts.QR_CREATE);
        return new FeatureUsageSyncRegistry(
                List.of(qrMenuSynchronizer, qrCreateSynchronizer),
                writableTransactionGuard
        );
    }
}
