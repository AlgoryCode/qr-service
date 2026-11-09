package com.ael.algoryqrservice.qrservice.service;

import com.ael.algoryqrservice.qrservice.order.service.TableSessionService;
import com.ael.algoryqrservice.billservice.application.TableBillService;
import com.ael.algoryqrservice.qrservice.order.service.OrderAuditService;
import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.billservice.infrastructure.persistence.entity.BillPayment;
import com.ael.algoryqrservice.qrservice.model.MenuOrder;
import com.ael.algoryqrservice.qrservice.model.MenuOrderItem;
import com.ael.algoryqrservice.staffservice.infrastructure.persistence.entity.MerchantStaff;
import com.ael.algoryqrservice.qrservice.model.RestaurantTable;
import com.ael.algoryqrservice.billservice.infrastructure.persistence.entity.TableBill;
import com.ael.algoryqrservice.billservice.infrastructure.persistence.entity.TableBillItem;
import com.ael.algoryqrservice.qrservice.model.TableSession;
import com.ael.algoryqrservice.billservice.dto.TableBillDtos;
import com.ael.algoryqrservice.qrservice.model.enums.MenuOrderStatus;
import com.ael.algoryqrservice.billservice.infrastructure.persistence.entity.enums.TableBillPaymentMethod;
import com.ael.algoryqrservice.billservice.infrastructure.persistence.entity.enums.TableBillStatus;
import com.ael.algoryqrservice.qrservice.model.enums.WaiterCommissionType;
import com.ael.algoryqrservice.billservice.infrastructure.persistence.repository.BillPaymentRepository;
import com.ael.algoryqrservice.qrservice.repository.MenuOrderRepository;
import com.ael.algoryqrservice.qrservice.product.repository.MenuProductRepository;
import com.ael.algoryqrservice.qrservice.repository.MenuRepository;
import com.ael.algoryqrservice.staffservice.infrastructure.persistence.repository.MerchantStaffRepository;
import com.ael.algoryqrservice.qrservice.repository.RestaurantTableRepository;
import com.ael.algoryqrservice.billservice.infrastructure.persistence.repository.TableBillItemRepository;
import com.ael.algoryqrservice.billservice.infrastructure.persistence.repository.TableBillRepository;
import com.ael.algoryqrservice.qrservice.repository.TableSessionRepository;
import com.ael.algoryqrservice.staffservice.application.StaffCommissionService;
import com.ael.algoryqrservice.qrservice.repository.BranchRepository;
import com.ael.algoryqrservice.qrservice.model.enums.OrderAuditAction;
import com.ael.algoryqrservice.qrservice.stock.service.StockConsumptionService;
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
    private MerchantStaffRepository merchantStaffRepository;
    @Mock
    private StaffCommissionService waiterCommissionService;
    @Mock
    private MenuOrderRepository menuOrderRepository;
    @Mock
    private BranchRepository branchRepository;
    @Mock
    private OrderAuditService orderAuditService;
    @Mock
    private StockConsumptionService stockConsumptionService;
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
        assertThat(updated.getItems().get(0).getAddedByStaffId()).isEqualTo(7L);
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

        MerchantStaff waiter = MerchantStaff.builder()
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
        MenuOrder sameTable = MenuOrder.builder().id(30L).menuId(1L).tableId(5L).status(MenuOrderStatus.READY).build();
        MenuOrder confirmed = MenuOrder.builder().id(32L).menuId(1L).tableId(5L).billId(10L)
                .status(MenuOrderStatus.CONFIRMED).build();
        MenuOrder preparing = MenuOrder.builder().id(33L).menuId(1L).tableId(5L).billId(10L)
                .status(MenuOrderStatus.PREPARING).build();
        MenuOrder otherBill = MenuOrder.builder().id(34L).menuId(1L).tableId(5L).billId(99L)
                .status(MenuOrderStatus.PREPARING).build();
        MenuOrder otherTable = MenuOrder.builder().id(31L).menuId(1L).tableId(9L).status(MenuOrderStatus.READY).build();
        when(menuRepository.findById(1L)).thenReturn(Optional.of(
                com.ael.algoryqrservice.qrservice.model.Menu.builder().menuId(1L).branchId(3L).build()));
        when(branchRepository.findById(3L)).thenReturn(Optional.of(
                com.ael.algoryqrservice.qrservice.model.Branch.builder().id(3L).kitchenEnabled(true).build()));
        when(menuOrderRepository.findByMenuIdAndTableIdAndStatusInOrderBySubmittedAtDesc(
                eq(1L), eq(5L), eq(List.of(MenuOrderStatus.CONFIRMED, MenuOrderStatus.PREPARING, MenuOrderStatus.READY))))
                .thenReturn(List.of(sameTable, confirmed, preparing, otherBill, otherTable));
        when(menuOrderRepository.save(any(MenuOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = tableBillService.closeBill(1L, 10L, waiter, TableBillPaymentMethod.CASH, false, null);

        assertThat(response.getStatus()).isEqualTo(TableBillStatus.CLOSED);
        assertThat(response.getPaymentMethod()).isEqualTo(TableBillPaymentMethod.CASH);
        assertThat(response.getFixedCommissionAmount()).isNull();
        assertThat(session.isRevoked()).isTrue();
        assertThat(sameTable.getStatus()).isEqualTo(MenuOrderStatus.SERVED);
        assertThat(confirmed.getStatus()).isEqualTo(MenuOrderStatus.SERVED);
        assertThat(preparing.getStatus()).isEqualTo(MenuOrderStatus.SERVED);
        assertThat(otherBill.getStatus()).isEqualTo(MenuOrderStatus.PREPARING);
        assertThat(otherTable.getStatus()).isEqualTo(MenuOrderStatus.READY);
        verify(menuOrderRepository).save(sameTable);
        verify(menuOrderRepository, never()).save(otherBill);
        verify(menuOrderRepository, never()).save(otherTable);
        verify(waiterCommissionService, never()).recordFixedTableCloseCommission(any(), any());
    }

    @Test
    void closeBill_whenPaymentMethodMissing_thenThrows() {
        MerchantStaff waiter = MerchantStaff.builder().id(7L).branchId(3L).build();

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
        MerchantStaff waiter = MerchantStaff.builder().id(7L).branchId(3L).build();

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
        MerchantStaff waiter = MerchantStaff.builder().id(7L).branchId(3L).build();
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

    @Test
    void payAmount_whenLessThanRemaining_thenRecordsUnallocatedPaymentAndKeepsBillOpen() {
        TableBill bill = openBillWithTotal(new BigDecimal("500.00"));
        MerchantStaff waiter = MerchantStaff.builder().id(7L).branchId(3L).build();
        List<BillPayment> payments = stubPaymentStore(bill);

        TableBillDtos.BillResponse response = tableBillService.payAmount(1L, 10L, waiter, amountRequest("200", TableBillPaymentMethod.CASH));

        assertThat(response.getStatus()).isEqualTo(TableBillStatus.OPEN);
        assertThat(response.getRemainingTotal()).isEqualByComparingTo("300.00");
        assertThat(response.getPaidTotal()).isEqualByComparingTo("200.00");
        assertThat(payments).singleElement().satisfies(payment -> {
            assertThat(payment.getAmount()).isEqualByComparingTo("200.00");
            assertThat(payment.getBillItem()).isNull();
            assertThat(payment.getSplitShareNumber()).isNull();
        });
        assertThat(response.getPayments().get(0).getItemSummary()).isEqualTo("Tutar ödemesi");
    }

    @Test
    void payAmount_whenMoreThanRemaining_thenThrows() {
        TableBill bill = openBillWithTotal(new BigDecimal("500.00"));
        MerchantStaff waiter = MerchantStaff.builder().id(7L).branchId(3L).build();
        stubPaymentStore(bill);

        assertThatThrownBy(() -> tableBillService.payAmount(1L, 10L, waiter, amountRequest("500.01", TableBillPaymentMethod.CASH)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("kalan tutardan");
    }

    @Test
    void payAmount_whenItCoversTheRest_thenClosesWithoutItemPayments() {
        TableBill bill = openBillWithTotal(new BigDecimal("500.00"));
        MerchantStaff waiter = MerchantStaff.builder().id(7L).branchId(3L).build();
        List<BillPayment> payments = stubPaymentStore(bill);
        when(tableSessionRepository.findByTableIdAndRevokedFalse(5L)).thenReturn(List.of());

        tableBillService.payAmount(1L, 10L, waiter, amountRequest("200", TableBillPaymentMethod.CASH));
        TableBillDtos.BillResponse response = tableBillService.payAmount(1L, 10L, waiter, amountRequest("300", TableBillPaymentMethod.CARD));

        assertThat(response.getStatus()).isEqualTo(TableBillStatus.CLOSED);
        assertThat(response.getPaymentMethod()).isEqualTo(TableBillPaymentMethod.CARD);
        assertThat(bill.getItems().get(0).getPaidQuantity()).isEqualTo(1);
        // Reports sum payments: the bill's 500 must be recorded once.
        assertThat(payments.stream().map(BillPayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("500.00");
        assertThat(payments).allSatisfy(payment -> assertThat(payment.getBillItem()).isNull());
    }

    @Test
    void closeBill_afterAmountPayment_recordsOnlyTheRest() {
        TableBill bill = openBillWithTotal(new BigDecimal("500.00"));
        MerchantStaff waiter = MerchantStaff.builder().id(7L).branchId(3L).build();
        List<BillPayment> payments = stubPaymentStore(bill);
        when(tableSessionRepository.findByTableIdAndRevokedFalse(5L)).thenReturn(List.of());

        tableBillService.payAmount(1L, 10L, waiter, amountRequest("120", TableBillPaymentMethod.CASH));
        TableBillDtos.BillResponse response = tableBillService.closeBill(1L, 10L, waiter, TableBillPaymentMethod.CARD, false, null);

        assertThat(response.getStatus()).isEqualTo(TableBillStatus.CLOSED);
        assertThat(payments).extracting(BillPayment::getAmount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("120.00"), new BigDecimal("380.00"));
    }

    @Test
    void changePaymentMethod_whenClosedBill_thenUpdatesPaymentsAndBillMethod() {
        TableBill bill = openBillWithTotal(new BigDecimal("500.00"));
        bill.setStatus(TableBillStatus.CLOSED);
        bill.setPaymentMethod(TableBillPaymentMethod.CASH);
        BillPayment payment = BillPayment.builder()
                .id(50L).bill(bill).amount(new BigDecimal("500.00"))
                .paymentMethod(TableBillPaymentMethod.CASH).paidAt(LocalDateTime.now()).build();
        when(tableBillRepository.findByIdAndMenuId(10L, 1L)).thenReturn(Optional.of(bill));
        when(tableBillRepository.save(any(TableBill.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(billPaymentRepository.findById(50L)).thenReturn(Optional.of(payment));
        when(billPaymentRepository.findByBillIdOrderByPaidAtAsc(10L)).thenReturn(List.of(payment));

        TableBillDtos.BillResponse response = tableBillService.changePaymentMethod(1L, 10L,
                TableBillDtos.ChangePaymentMethodRequest.builder()
                        .paymentIds(List.of(50L))
                        .paymentMethod(TableBillPaymentMethod.CARD)
                        .build());

        assertThat(payment.getPaymentMethod()).isEqualTo(TableBillPaymentMethod.CARD);
        assertThat(response.getPaymentMethod()).isEqualTo(TableBillPaymentMethod.CARD);
    }

    @Test
    void changePaymentMethod_whenPaymentIsFromAnotherDay_thenThrows() {
        TableBill bill = openBillWithTotal(new BigDecimal("500.00"));
        BillPayment payment = BillPayment.builder()
                .id(50L).bill(bill).amount(new BigDecimal("500.00"))
                .paymentMethod(TableBillPaymentMethod.CASH).paidAt(LocalDateTime.now().minusDays(1)).build();
        when(tableBillRepository.findByIdAndMenuId(10L, 1L)).thenReturn(Optional.of(bill));
        when(billPaymentRepository.findById(50L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> tableBillService.changePaymentMethod(1L, 10L,
                TableBillDtos.ChangePaymentMethodRequest.builder()
                        .paymentIds(List.of(50L))
                        .paymentMethod(TableBillPaymentMethod.CARD)
                        .build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("bugün");
    }

    @Test
    void payShare_whenLastShareCloses_thenRevenueIsRecordedOnce() {
        TableBill bill = openBillWithTotal(new BigDecimal("315.00"));
        MerchantStaff waiter = MerchantStaff.builder().id(7L).branchId(3L).build();
        List<BillPayment> payments = stubPaymentStore(bill);
        when(tableSessionRepository.findByTableIdAndRevokedFalse(5L)).thenReturn(List.of());

        for (int share = 1; share <= 2; share++) {
            tableBillService.payShare(1L, 10L, waiter, TableBillDtos.PayBillShareRequest.builder()
                    .personCount(2)
                    .shareNumber(share)
                    .paymentMethod(TableBillPaymentMethod.CASH)
                    .build());
        }

        assertThat(bill.getStatus()).isEqualTo(TableBillStatus.CLOSED);
        assertThat(bill.getItems().get(0).getPaidQuantity()).isEqualTo(1);
        // Reports sum payments: two shares, no item payments on top.
        assertThat(payments).hasSize(2).allSatisfy(payment -> assertThat(payment.getBillItem()).isNull());
        assertThat(payments.stream().map(BillPayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("315.00");
    }

    @Test
    void updateItemQuantity_whenPiecesComeOffTheBill_thenShrinksTheSourceOrder() {
        TableBill bill = billWithOrderLine(2, 50L);
        MenuOrder order = orderWith(50L, orderItem(1L, 100L, 2, "190.00"), orderItem(2L, 200L, 1, "90.00"));
        stubBillItemEdit(bill, order);

        tableBillService.updateItemQuantity(1L, 10L, 1L, 1, 7L);

        assertThat(order.getStatus()).isEqualTo(MenuOrderStatus.SERVED);
        assertThat(order.getItems()).hasSize(2);
        assertThat(order.getItems().get(0).getQuantity()).isEqualTo(1);
        assertThat(order.getItems().get(0).getLineTotal()).isEqualByComparingTo("95.00");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("185.00");
        verify(orderAuditService).record(eq(order), eq(OrderAuditAction.ITEM_REMOVED), eq(7L), any());
        verify(stockConsumptionService, never()).reverseMenuOrder(any());
    }

    @Test
    void removeItem_whenItWasTheWholeOrder_thenCancelsTheOrderAndReturnsStock() {
        TableBill bill = billWithOrderLine(2, 50L);
        MenuOrder order = orderWith(50L, orderItem(1L, 100L, 2, "190.00"));
        stubBillItemEdit(bill, order);

        TableBillDtos.BillResponse response = tableBillService.removeItem(1L, 10L, 1L, 7L);

        assertThat(response.getItems()).isEmpty();
        assertThat(order.getStatus()).isEqualTo(MenuOrderStatus.CANCELLED);
        assertThat(order.getCancelledByStaffId()).isEqualTo(7L);
        assertThat(order.getItems()).singleElement().satisfies(item -> assertThat(item.getQuantity()).isEqualTo(2));
        verify(stockConsumptionService).reverseMenuOrder(order);
        verify(orderAuditService).record(eq(order), eq(OrderAuditAction.CANCELLED), eq(7L), any());
    }

    @Test
    void removeItem_whenLineWasAddedWithoutAnOrder_thenLeavesOrdersAlone() {
        TableBill bill = billWithOrderLine(1, null);
        when(tableBillRepository.findByIdAndMenuIdAndStatus(10L, 1L, TableBillStatus.OPEN)).thenReturn(Optional.of(bill));
        when(tableBillItemRepository.findByIdAndBillId(1L, 10L)).thenReturn(Optional.of(bill.getItems().get(0)));
        when(tableBillRepository.save(any(TableBill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        tableBillService.removeItem(1L, 10L, 1L, 7L);

        verify(menuOrderRepository, never()).findById(any());
    }

    @Test
    void removeItem_afterASplitShareWasPaid_thenThrowsAndKeepsTheOrder() {
        TableBill bill = billWithOrderLine(2, 50L);
        when(tableBillRepository.findByIdAndMenuIdAndStatus(10L, 1L, TableBillStatus.OPEN)).thenReturn(Optional.of(bill));
        when(tableBillItemRepository.findByIdAndBillId(1L, 10L)).thenReturn(Optional.of(bill.getItems().get(0)));
        when(billPaymentRepository.findByBillIdOrderByPaidAtAsc(10L)).thenReturn(List.of(BillPayment.builder()
                .id(1L).amount(new BigDecimal("95.00")).splitShareNumber(1).splitPersonCount(2).build()));

        assertThatThrownBy(() -> tableBillService.removeItem(1L, 10L, 1L, 7L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Kişi başı");
        assertThat(bill.getItems()).hasSize(1);
        verify(menuOrderRepository, never()).findById(any());
    }

    @Test
    void updateItemQuantity_whenItWouldDropBelowAnAmountPayment_thenThrows() {
        TableBill bill = billWithOrderLine(2, 50L);
        when(tableBillRepository.findByIdAndMenuIdAndStatus(10L, 1L, TableBillStatus.OPEN)).thenReturn(Optional.of(bill));
        when(tableBillItemRepository.findByIdAndBillId(1L, 10L)).thenReturn(Optional.of(bill.getItems().get(0)));
        when(billPaymentRepository.findByBillIdOrderByPaidAtAsc(10L)).thenReturn(List.of(BillPayment.builder()
                .id(1L).amount(new BigDecimal("150.00")).build()));

        assertThatThrownBy(() -> tableBillService.updateItemQuantity(1L, 10L, 1L, 1, 7L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("alınan ödemenin altına");
    }

    @Test
    void addTip_whenBillClosedToday_thenRecordsATipPaymentAndAddsUp() {
        TableBill bill = openBillWithTotal(new BigDecimal("90.00"));
        bill.setStatus(TableBillStatus.CLOSED);
        bill.setClosedAt(LocalDateTime.now());
        bill.setTipAmount(new BigDecimal("10.00"));
        MerchantStaff waiter = MerchantStaff.builder().id(7L).branchId(3L).build();
        when(tableBillRepository.findByIdAndMenuId(10L, 1L)).thenReturn(Optional.of(bill));
        when(tableBillRepository.save(any(TableBill.class))).thenAnswer(invocation -> invocation.getArgument(0));
        List<BillPayment> saved = new ArrayList<>();
        when(billPaymentRepository.save(any(BillPayment.class))).thenAnswer(invocation -> {
            saved.add(invocation.getArgument(0));
            return invocation.getArgument(0);
        });

        tableBillService.addTip(1L, 10L, waiter, TableBillDtos.AddTipRequest.builder()
                .amount(new BigDecimal("15.00")).paymentMethod(TableBillPaymentMethod.CARD).build());

        assertThat(saved).singleElement().satisfies(payment -> {
            assertThat(payment.isTip()).isTrue();
            assertThat(payment.getAmount()).isEqualByComparingTo("15.00");
            assertThat(payment.getStaffId()).isEqualTo(7L);
            assertThat(payment.getPaymentMethod()).isEqualTo(TableBillPaymentMethod.CARD);
        });
        assertThat(bill.getTipAmount()).isEqualByComparingTo("25.00");
    }

    @Test
    void addTip_whenBillStillOpen_thenThrows() {
        TableBill bill = openBillWithTotal(new BigDecimal("90.00"));
        when(tableBillRepository.findByIdAndMenuId(10L, 1L)).thenReturn(Optional.of(bill));

        assertThatThrownBy(() -> tableBillService.addTip(1L, 10L, MerchantStaff.builder().id(7L).build(),
                TableBillDtos.AddTipRequest.builder().amount(BigDecimal.TEN).paymentMethod(TableBillPaymentMethod.CASH).build()))
                .isInstanceOf(BadRequestException.class);
        verify(billPaymentRepository, never()).save(any());
    }

    @Test
    void addTip_whenBillClosedOnAnotherDay_thenThrows() {
        TableBill bill = openBillWithTotal(new BigDecimal("90.00"));
        bill.setStatus(TableBillStatus.CLOSED);
        bill.setClosedAt(LocalDateTime.now().minusDays(1));
        when(tableBillRepository.findByIdAndMenuId(10L, 1L)).thenReturn(Optional.of(bill));

        assertThatThrownBy(() -> tableBillService.addTip(1L, 10L, MerchantStaff.builder().id(7L).build(),
                TableBillDtos.AddTipRequest.builder().amount(BigDecimal.TEN).paymentMethod(TableBillPaymentMethod.CASH).build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("bugün");
    }

    private void stubBillItemEdit(TableBill bill, MenuOrder order) {
        when(tableBillRepository.findByIdAndMenuIdAndStatus(10L, 1L, TableBillStatus.OPEN)).thenReturn(Optional.of(bill));
        when(tableBillItemRepository.findByIdAndBillId(1L, 10L)).thenReturn(Optional.of(bill.getItems().get(0)));
        when(tableBillRepository.save(any(TableBill.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(menuOrderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(menuOrderRepository.save(any(MenuOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private TableBill billWithOrderLine(int quantity, Long sourceOrderId) {
        TableBill bill = openBillWithTotal(new BigDecimal("95.00").multiply(BigDecimal.valueOf(quantity)));
        TableBillItem item = bill.getItems().get(0);
        item.setUnitPrice(new BigDecimal("95.00"));
        item.setQuantity(quantity);
        item.setSourceOrderId(sourceOrderId);
        return bill;
    }

    private MenuOrder orderWith(Long id, MenuOrderItem... items) {
        MenuOrder order = MenuOrder.builder()
                .id(id)
                .menuId(1L)
                .status(MenuOrderStatus.SERVED)
                .items(new ArrayList<>(List.of(items)))
                .build();
        for (MenuOrderItem item : items) {
            item.setOrder(order);
        }
        return order;
    }

    private MenuOrderItem orderItem(Long id, Long productId, int quantity, String lineTotal) {
        return MenuOrderItem.builder()
                .id(id)
                .productId(productId)
                .productName("Ürün " + productId)
                .unitPrice(new BigDecimal(lineTotal).divide(BigDecimal.valueOf(quantity)))
                .quantity(quantity)
                .lineTotal(new BigDecimal(lineTotal))
                .build();
    }

    /** An in-memory payment table, so remaining amounts follow what the service saved. */
    private List<BillPayment> stubPaymentStore(TableBill bill) {
        List<BillPayment> payments = new ArrayList<>();
        when(tableBillRepository.findByIdAndMenuIdAndStatus(10L, 1L, TableBillStatus.OPEN))
                .thenAnswer(invocation -> bill.getStatus() == TableBillStatus.OPEN ? Optional.of(bill) : Optional.empty());
        org.mockito.Mockito.lenient().when(tableBillRepository.save(any(TableBill.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(billPaymentRepository.findByBillIdOrderByPaidAtAsc(10L)).thenAnswer(invocation -> List.copyOf(payments));
        org.mockito.Mockito.lenient().when(billPaymentRepository.save(any(BillPayment.class))).thenAnswer(invocation -> {
            BillPayment payment = invocation.getArgument(0);
            payment.setId(100L + payments.size());
            payments.add(payment);
            return payment;
        });
        return payments;
    }

    private TableBillDtos.PayBillAmountRequest amountRequest(String amount, TableBillPaymentMethod method) {
        return TableBillDtos.PayBillAmountRequest.builder()
                .amount(new BigDecimal(amount))
                .paymentMethod(method)
                .build();
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
