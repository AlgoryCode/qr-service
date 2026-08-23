package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.MenuOrder;
import com.ael.algoryqrservice.model.MenuProduct;
import com.ael.algoryqrservice.model.MenuWaiter;
import com.ael.algoryqrservice.model.SubCategory;
import com.ael.algoryqrservice.model.TableBill;
import com.ael.algoryqrservice.model.WaiterCommissionRecord;
import com.ael.algoryqrservice.model.enums.WaiterCommissionRecordType;
import com.ael.algoryqrservice.model.enums.WaiterCommissionType;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import com.ael.algoryqrservice.repository.WaiterCommissionRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WaiterCommissionServiceTest {

    @Mock
    private WaiterCommissionRecordRepository commissionRecordRepository;
    @Mock
    private MenuTaxonomyService menuTaxonomyService;
    @Mock
    private MenuProductRepository menuProductRepository;

    @InjectMocks
    private WaiterCommissionService waiterCommissionService;

    @Test
    void recordPercentOrderCommission_whenEnabled_thenCalculatesAndPersists() {
        MenuWaiter waiter = MenuWaiter.builder()
                .id(7L)
                .branchId(3L)
                .commissionEnabled(true)
                .commissionType(WaiterCommissionType.PERCENT)
                .commissionValue(new BigDecimal("10"))
                .build();

        MenuOrder order = MenuOrder.builder()
                .id(20L)
                .menuId(1L)
                .totalAmount(new BigDecimal("200.00"))
                .currency("TRY")
                .build();

        when(commissionRecordRepository.save(any(WaiterCommissionRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BigDecimal amount = waiterCommissionService.recordPercentOrderCommission(waiter, order, 10L);

        assertThat(amount).isEqualByComparingTo("20.00");
        assertThat(order.getCommissionAmount()).isEqualByComparingTo("20.00");

        ArgumentCaptor<WaiterCommissionRecord> captor = ArgumentCaptor.forClass(WaiterCommissionRecord.class);
        verify(commissionRecordRepository).save(captor.capture());
        WaiterCommissionRecord saved = captor.getValue();
        assertThat(saved.getRecordType()).isEqualTo(WaiterCommissionRecordType.PERCENT_ORDER);
        assertThat(saved.getOrderId()).isEqualTo(20L);
        assertThat(saved.getBillId()).isEqualTo(10L);
    }

    @Test
    void recordFixedTableCloseCommission_whenEnabled_thenPersistsFixedAmount() {
        MenuWaiter waiter = MenuWaiter.builder()
                .id(7L)
                .branchId(3L)
                .commissionEnabled(true)
                .commissionType(WaiterCommissionType.FIXED)
                .commissionValue(new BigDecimal("25.00"))
                .build();

        TableBill bill = TableBill.builder()
                .id(10L)
                .menuId(1L)
                .totalAmount(new BigDecimal("150.00"))
                .currency("TRY")
                .build();

        when(commissionRecordRepository.save(any(WaiterCommissionRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BigDecimal amount = waiterCommissionService.recordFixedTableCloseCommission(waiter, bill);

        assertThat(amount).isEqualByComparingTo("25.00");

        ArgumentCaptor<WaiterCommissionRecord> captor = ArgumentCaptor.forClass(WaiterCommissionRecord.class);
        verify(commissionRecordRepository).save(captor.capture());
        assertThat(captor.getValue().getRecordType()).isEqualTo(WaiterCommissionRecordType.FIXED_TABLE_CLOSE);
        assertThat(captor.getValue().getOrderId()).isNull();
    }

    @Test
    void recordFixedItemAddCommission_whenEligibleProduct_thenPersistsPerQuantity() {
        MenuWaiter waiter = MenuWaiter.builder()
                .id(7L)
                .branchId(3L)
                .commissionEnabled(true)
                .commissionType(WaiterCommissionType.FIXED)
                .commissionValue(new BigDecimal("5.00"))
                .build();

        SubCategory drinks = SubCategory.builder().id(2L).slug("soguk_icecekler").name("Soğuk").build();
        MenuProduct product = MenuProduct.builder()
                .productId(100L)
                .subCategoryId(2L)
                .name("Kola")
                .build();

        when(menuTaxonomyService.loadSubCategoryMap()).thenReturn(Map.of(2L, drinks));
        when(menuProductRepository.findByProductIdAndDeletedFalse(100L)).thenReturn(Optional.of(product));
        when(commissionRecordRepository.save(any(WaiterCommissionRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BigDecimal amount = waiterCommissionService.recordFixedItemAddCommission(
                waiter,
                1L,
                10L,
                20L,
                List.of(new WaiterCommissionService.CommissionLineItem(100L, 2)),
                "TRY"
        );

        assertThat(amount).isEqualByComparingTo("10.00");

        ArgumentCaptor<WaiterCommissionRecord> captor = ArgumentCaptor.forClass(WaiterCommissionRecord.class);
        verify(commissionRecordRepository).save(captor.capture());
        assertThat(captor.getValue().getRecordType()).isEqualTo(WaiterCommissionRecordType.FIXED_ITEM_ADD);
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("10.00");
    }

    @Test
    void recordFixedItemAddCommission_whenSuCategory_thenSkipsCommission() {
        MenuWaiter waiter = MenuWaiter.builder()
                .id(7L)
                .branchId(3L)
                .commissionEnabled(true)
                .commissionType(WaiterCommissionType.FIXED)
                .commissionValue(new BigDecimal("5.00"))
                .build();

        SubCategory water = SubCategory.builder().id(82L).slug("su").name("Su").build();
        MenuProduct product = MenuProduct.builder()
                .productId(200L)
                .subCategoryId(82L)
                .name("Su")
                .build();

        when(menuTaxonomyService.loadSubCategoryMap()).thenReturn(Map.of(82L, water));
        when(menuProductRepository.findByProductIdAndDeletedFalse(200L)).thenReturn(Optional.of(product));

        BigDecimal amount = waiterCommissionService.recordFixedItemAddCommission(
                waiter,
                1L,
                10L,
                null,
                List.of(new WaiterCommissionService.CommissionLineItem(200L, 3)),
                "TRY"
        );

        assertThat(amount).isEqualByComparingTo("0");
        verify(commissionRecordRepository, never()).save(any());
    }

    @Test
    void recordPercentOrderCommission_whenDisabled_thenReturnsZero() {
        MenuWaiter waiter = MenuWaiter.builder()
                .id(7L)
                .commissionEnabled(false)
                .build();
        MenuOrder order = MenuOrder.builder().totalAmount(new BigDecimal("100.00")).build();

        BigDecimal amount = waiterCommissionService.recordPercentOrderCommission(waiter, order, 10L);

        assertThat(amount).isEqualByComparingTo("0");
    }
}
