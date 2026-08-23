package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.BillPayment;
import com.ael.algoryqrservice.model.MenuOrder;
import com.ael.algoryqrservice.model.MenuOrderItem;
import com.ael.algoryqrservice.model.MenuWaiter;
import com.ael.algoryqrservice.model.RestaurantTable;
import com.ael.algoryqrservice.model.TableBill;
import com.ael.algoryqrservice.model.TableBillItem;
import com.ael.algoryqrservice.model.TableSession;
import com.ael.algoryqrservice.model.dto.TableBillDtos;
import com.ael.algoryqrservice.model.enums.MenuOrderStatus;
import com.ael.algoryqrservice.model.enums.TableBillPaymentMethod;
import com.ael.algoryqrservice.model.enums.TableBillStatus;
import com.ael.algoryqrservice.model.enums.WaiterCommissionType;
import com.ael.algoryqrservice.repository.BillPaymentRepository;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.MenuWaiterRepository;
import com.ael.algoryqrservice.repository.RestaurantTableRepository;
import com.ael.algoryqrservice.repository.TableBillItemRepository;
import com.ael.algoryqrservice.repository.TableBillRepository;
import com.ael.algoryqrservice.repository.TableSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TableBillServiceTest {

    @Mock
    private TableBillRepository tableBillRepository;
    @Mock
    private TableBillItemRepository tableBillItemRepository;
    @Mock
    private BillPaymentRepository billPaymentRepository;
    @Mock
    private TableSessionRepository tableSessionRepository;
    @Mock
    private TableSessionService tableSessionService;
    @Mock
    private MenuProductRepository menuProductRepository;
    @Mock
    private MenuRepository menuRepository;
    @Mock
    private RestaurantTableRepository restaurantTableRepository;
    @Mock
    private MenuWaiterRepository menuWaiterRepository;
    @Mock
    private WaiterCommissionService waiterCommissionService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private TableBillService tableBillService;

    @Test
    void getOrOpenBill_whenOpenBillExists_thenReturnsExisting() {
        TableBill existing = TableBill.builder()
                .id(10L)
                .menuId(1L)
                .tableId(5L)
                .status(TableBillStatus.OPEN)
                .totalAmount(BigDecimal.TEN)
                .items(new ArrayList<>())
                .build();

        when(tableBillRepository.findByMenuIdAndTableIdAndStatus(1L, 5L, TableBillStatus.OPEN))
                .thenReturn(Optional.of(existing));

        TableBill result = tableBillService.getOrOpenBill(1L, 5L, 99L);

        assertThat(result.getId()).isEqualTo(10L);
        verify(tableSessionService, never()).openInternalSession(any(), any());
    }

    @Test
    void addItemsFromOrder_whenBillOpen_thenAddsItemsAndRecalculatesTotal() {
        TableBill bill = TableBill.builder()
                .id(10L)
                .menuId(1L)
                .tableId(5L)
                .status(TableBillStatus.OPEN)
                .totalAmount(BigDecimal.ZERO)
                .currency("TRY")
                .items(new ArrayList<>())
                .build();

        MenuOrder order = MenuOrder.builder()
                .id(20L)
                .status(MenuOrderStatus.CONFIRMED)
                .items(List.of(
                        MenuOrderItem.builder()
                                .productId(100L)
                                .productName("Çay")
                                .unitPrice(new BigDecimal("25.00"))
                                .quantity(2)
                                .lineTotal(new BigDecimal("50.00"))
                                .build()
                ))
                .build();

        when(tableBillRepository.save(any(TableBill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TableBill updated = tableBillService.addItemsFromOrder(bill, order, 7L);

        assertThat(updated.getItems()).hasSize(1);
        assertThat(updated.getTotalAmount()).isEqualByComparingTo("50.00");
        assertThat(updated.getItems().get(0).getSourceOrderId()).isEqualTo(20L);
        assertThat(updated.getItems().get(0).getAddedByWaiterId()).isEqualTo(7L);
    }

    @Test
    void closeBill_whenPaymentMethodProvided_thenClosesBillAndRevokesSessions() {
        TableBillItem item = TableBillItem.builder()
                .id(1L)
                .productId(100L)
                .productName("Kahve")
                .unitPrice(new BigDecimal("40.00"))
                .quantity(1)
                .lineTotal(new BigDecimal("40.00"))
                .build();

        TableBill bill = TableBill.builder()
                .id(10L)
                .menuId(1L)
                .tableId(5L)
                .status(TableBillStatus.OPEN)
                .totalAmount(new BigDecimal("40.00"))
                .currency("TRY")
                .items(new ArrayList<>(List.of(item)))
                .build();
        item.setBill(bill);

        MenuWaiter waiter = MenuWaiter.builder()
                .id(7L)
                .branchId(3L)
                .commissionEnabled(true)
                .commissionType(WaiterCommissionType.FIXED)
                .commissionValue(new BigDecimal("15.00"))
                .build();

        TableSession session = TableSession.builder()
                .id(UUID.randomUUID())
                .tableId(5L)
                .menuId(1L)
                .sessionToken("token")
                .expiresAt(LocalDateTime.now().plusHours(2))
                .revoked(false)
                .createdAt(LocalDateTime.now())
                .build();

        when(tableBillRepository.findByIdAndMenuIdAndStatus(10L, 1L, TableBillStatus.OPEN))
                .thenReturn(Optional.of(bill));
        when(tableBillRepository.save(any(TableBill.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tableSessionRepository.findByTableIdAndRevokedFalse(5L)).thenReturn(List.of(session));
        when(billPaymentRepository.findByBillIdOrderByPaidAtAsc(10L)).thenReturn(List.of());
        when(billPaymentRepository.save(any(BillPayment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = tableBillService.closeBill(1L, 10L, waiter, TableBillPaymentMethod.CASH, false, null);

        assertThat(response.getStatus()).isEqualTo(TableBillStatus.CLOSED);
        assertThat(response.getPaymentMethod()).isEqualTo(TableBillPaymentMethod.CASH);
        assertThat(response.getFixedCommissionAmount()).isNull();
        assertThat(session.isRevoked()).isTrue();
        verify(waiterCommissionService, never()).recordFixedTableCloseCommission(any(), any());
    }

    @Test
    void closeBill_whenPaymentMethodMissing_thenThrows() {
        MenuWaiter waiter = MenuWaiter.builder().id(7L).branchId(3L).build();

        assertThatThrownBy(() -> tableBillService.closeBill(1L, 10L, waiter, null, false, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Ödeme yöntemi");
    }

    @Test
    void openBill_whenOpenBillAlreadyExists_thenThrows() {
        RestaurantTable table = RestaurantTable.builder()
                .id(5L)
                .menuId(1L)
                .active(true)
                .build();

        when(restaurantTableRepository.findByIdAndMenuId(5L, 1L)).thenReturn(Optional.of(table));
        when(tableBillRepository.findByMenuIdAndTableIdAndStatus(1L, 5L, TableBillStatus.OPEN))
                .thenReturn(Optional.of(TableBill.builder().id(99L).build()));

        assertThatThrownBy(() -> tableBillService.openBill(1L, 5L, 7L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("açık bir adisyon");
    }

    @Test
    void getSplitPreview_when503DividedBy5_thenDistributesRemainderOnLastShare() {
        TableBillItem item = TableBillItem.builder()
                .id(1L)
                .productId(100L)
                .productName("Menü")
                .unitPrice(new BigDecimal("503.03"))
                .quantity(1)
                .paidQuantity(0)
                .lineTotal(new BigDecimal("503.03"))
                .build();

        TableBill bill = TableBill.builder()
                .id(10L)
                .menuId(1L)
                .tableId(5L)
                .status(TableBillStatus.OPEN)
                .totalAmount(new BigDecimal("503.03"))
                .currency("TRY")
                .items(new ArrayList<>(List.of(item)))
                .build();
        item.setBill(bill);

        when(tableBillRepository.findByIdAndMenuIdAndStatus(10L, 1L, TableBillStatus.OPEN))
                .thenReturn(Optional.of(bill));
        when(billPaymentRepository.findByBillIdOrderByPaidAtAsc(10L)).thenReturn(List.of());

        TableBillDtos.SplitPreviewResponse preview = tableBillService.getSplitPreview(1L, 10L, 5);

        assertThat(preview.getRemainingTotal()).isEqualByComparingTo("503.03");
        assertThat(preview.getShares()).hasSize(5);
        assertThat(preview.getShares().get(0).getAmount()).isEqualByComparingTo("100.60");
        assertThat(preview.getShares().get(4).getAmount()).isEqualByComparingTo("100.63");
        assertThat(preview.getShares().stream()
                .map(TableBillDtos.SplitSharePreview::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("503.03");
    }

    @Test
    void payShare_whenDuplicateShare_thenThrows() {
        TableBill bill = openBillWithTotal(new BigDecimal("500.00"));
        MenuWaiter waiter = MenuWaiter.builder().id(7L).branchId(3L).build();

        when(tableBillRepository.findByIdAndMenuIdAndStatus(10L, 1L, TableBillStatus.OPEN))
                .thenReturn(Optional.of(bill));
        when(billPaymentRepository.findByBillIdOrderByPaidAtAsc(10L)).thenReturn(List.of());
        when(billPaymentRepository.existsByBillIdAndSplitShareNumber(10L, 1)).thenReturn(true);

        TableBillDtos.PayBillShareRequest request = TableBillDtos.PayBillShareRequest.builder()
                .personCount(5)
                .shareNumber(1)
                .paymentMethod(TableBillPaymentMethod.CASH)
                .build();

        assertThatThrownBy(() -> tableBillService.payShare(1L, 10L, waiter, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("zaten ödendi");
    }

    @Test
    void payShare_whenAllSharesPaid_thenClosesBill() {
        TableBill bill = openBillWithTotal(new BigDecimal("500.00"));
        MenuWaiter waiter = MenuWaiter.builder().id(7L).branchId(3L).build();
        TableSession session = TableSession.builder()
                .id(UUID.randomUUID())
                .tableId(5L)
                .menuId(1L)
                .sessionToken("token")
                .expiresAt(LocalDateTime.now().plusHours(2))
                .revoked(false)
                .createdAt(LocalDateTime.now())
                .build();

        when(tableBillRepository.findByIdAndMenuIdAndStatus(10L, 1L, TableBillStatus.OPEN))
                .thenReturn(Optional.of(bill));
        when(tableBillRepository.save(any(TableBill.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tableSessionRepository.findByTableIdAndRevokedFalse(5L)).thenReturn(List.of(session));
        when(billPaymentRepository.save(any(BillPayment.class))).thenAnswer(invocation -> {
            BillPayment payment = invocation.getArgument(0);
            payment.setId(100L + (payment.getSplitShareNumber() != null ? payment.getSplitShareNumber() : 0));
            return payment;
        });

        List<BillPayment> existing = new ArrayList<>();
        when(billPaymentRepository.findByBillIdOrderByPaidAtAsc(10L)).thenAnswer(invocation -> List.copyOf(existing));
        when(billPaymentRepository.existsByBillIdAndSplitShareNumber(eq(10L), any())).thenAnswer(invocation -> {
            Integer shareNumber = invocation.getArgument(1);
            return existing.stream().anyMatch(p -> shareNumber.equals(p.getSplitShareNumber()));
        });

        for (int share = 1; share <= 5; share++) {
            TableBillDtos.PayBillShareRequest request = TableBillDtos.PayBillShareRequest.builder()
                    .personCount(5)
                    .shareNumber(share)
                    .paymentMethod(TableBillPaymentMethod.CARD)
                    .build();
            TableBillDtos.BillResponse response = tableBillService.payShare(1L, 10L, waiter, request);
            existing.add(BillPayment.builder()
                    .id((long) share)
                    .splitShareNumber(share)
                    .splitPersonCount(5)
                    .amount(new BigDecimal("100.00"))
                    .tip(false)
                    .build());
            if (share < 5) {
                assertThat(response.getStatus()).isEqualTo(TableBillStatus.OPEN);
            } else {
                assertThat(response.getStatus()).isEqualTo(TableBillStatus.CLOSED);
            }
        }

        assertThat(session.isRevoked()).isTrue();
    }

    @Test
    void getSplitPreview_afterItemPayment_usesRemainingTotal() {
        TableBillItem item = TableBillItem.builder()
                .id(1L)
                .productId(100L)
                .productName("Kahve")
                .unitPrice(new BigDecimal("100.00"))
                .quantity(5)
                .paidQuantity(2)
                .lineTotal(new BigDecimal("500.00"))
                .build();

        TableBill bill = TableBill.builder()
                .id(10L)
                .menuId(1L)
                .tableId(5L)
                .status(TableBillStatus.OPEN)
                .totalAmount(new BigDecimal("500.00"))
                .currency("TRY")
                .items(new ArrayList<>(List.of(item)))
                .build();
        item.setBill(bill);

        when(tableBillRepository.findByIdAndMenuIdAndStatus(10L, 1L, TableBillStatus.OPEN))
                .thenReturn(Optional.of(bill));
        when(billPaymentRepository.findByBillIdOrderByPaidAtAsc(10L)).thenReturn(List.of());

        TableBillDtos.SplitPreviewResponse preview = tableBillService.getSplitPreview(1L, 10L, 2);

        assertThat(preview.getRemainingTotal()).isEqualByComparingTo("300.00");
        assertThat(preview.getShares().get(0).getAmount()).isEqualByComparingTo("150.00");
        assertThat(preview.getShares().get(1).getAmount()).isEqualByComparingTo("150.00");
    }

    private TableBill openBillWithTotal(BigDecimal total) {
        TableBillItem item = TableBillItem.builder()
                .id(1L)
                .productId(100L)
                .productName("Yemek")
                .unitPrice(total)
                .quantity(1)
                .paidQuantity(0)
                .lineTotal(total)
                .build();

        TableBill bill = TableBill.builder()
                .id(10L)
                .menuId(1L)
                .tableId(5L)
                .status(TableBillStatus.OPEN)
                .totalAmount(total)
                .currency("TRY")
                .items(new ArrayList<>(List.of(item)))
                .build();
        item.setBill(bill);
        return bill;
    }
}
