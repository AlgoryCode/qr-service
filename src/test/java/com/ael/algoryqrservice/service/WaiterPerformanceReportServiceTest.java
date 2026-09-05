package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.BillPayment;
import com.ael.algoryqrservice.model.MenuWaiter;
import com.ael.algoryqrservice.model.TableBill;
import com.ael.algoryqrservice.model.TableBillItem;
import com.ael.algoryqrservice.model.WaiterCommissionRecord;
import com.ael.algoryqrservice.model.dto.AnalyticsDtos;
import com.ael.algoryqrservice.model.enums.TableBillPaymentMethod;
import com.ael.algoryqrservice.model.enums.TableBillStatus;
import com.ael.algoryqrservice.model.enums.WaiterCommissionRecordType;
import com.ael.algoryqrservice.repository.BillPaymentRepository;
import com.ael.algoryqrservice.repository.TableBillRepository;
import com.ael.algoryqrservice.repository.WaiterCommissionRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WaiterPerformanceReportServiceTest {

    @Mock
    private BillPaymentRepository billPaymentRepository;
    @Mock
    private TableBillRepository tableBillRepository;
    @Mock
    private WaiterCommissionRecordRepository commissionRecordRepository;

    private WaiterPerformanceReportService service;

    @BeforeEach
    void setUp() {
        service = new WaiterPerformanceReportService(
                billPaymentRepository,
                tableBillRepository,
                commissionRecordRepository
        );
    }

    @Test
    void build_whenPaymentsExist_thenGroupsSalesByWaiter() {
        Long menuId = 5L;
        LocalDate day = LocalDate.of(2026, 8, 13);
        MenuWaiter ali = waiter(101L, "Ali", true);
        MenuWaiter ayse = waiter(102L, "Ayse", true);

        TableBill aliBill = bill(10L, menuId, 101L);
        TableBill ayseBill = bill(11L, menuId, 102L);
        TableBill unassignedBill = bill(12L, menuId, null);

        TableBillItem cay = item(aliBill, 11L, "Cay");
        TableBillItem ayran = item(ayseBill, 12L, "Ayran");
        TableBillItem su = item(unassignedBill, 13L, "Su");

        when(billPaymentRepository.findByMenuIdInAndPaidAtBetween(eq(List.of(menuId)), any(), any()))
                .thenReturn(List.of(
                        payment(aliBill, cay, 101L, "150.00", 2, false, LocalDateTime.of(2026, 8, 13, 14, 0)),
                        payment(ayseBill, ayran, 102L, "90.00", 1, false, LocalDateTime.of(2026, 8, 13, 15, 0)),
                        payment(unassignedBill, su, null, "20.00", 1, false, LocalDateTime.of(2026, 8, 13, 16, 0)),
                        payment(aliBill, null, 101L, "10.00", 0, true, LocalDateTime.of(2026, 8, 13, 14, 5))
                ));
        when(tableBillRepository.findByMenuIdInAndStatusAndClosedAtBetween(
                eq(List.of(menuId)), eq(TableBillStatus.CLOSED), any(), any()
        )).thenReturn(List.of(aliBill, ayseBill, unassignedBill));
        when(commissionRecordRepository.findByWaiterIdInAndCreatedAtBetween(eq(List.of(101L, 102L)), any(), any()))
                .thenReturn(List.of(
                        commission(101L, menuId, "15.00"),
                        commission(102L, menuId, "9.00")
                ));

        AnalyticsDtos.MenuWaiterPerformanceReportResponse report = service.build(
                menuId,
                "Test",
                2L,
                "Kadikoy",
                List.of(menuId),
                List.of(ali, ayse),
                Map.of(101L, "Ali", 102L, "Ayse"),
                day,
                day
        );

        assertThat(report.kpis().assignedOrderCount()).isEqualTo(2L);
        assertThat(report.kpis().unassignedOrderCount()).isEqualTo(1L);
        assertThat(report.kpis().totalRevenue()).isEqualByComparingTo("260.00");
        assertThat(report.kpis().totalTip()).isEqualByComparingTo("10.00");
        assertThat(report.kpis().itemCount()).isEqualTo(4L);
        assertThat(report.kpis().totalCommission()).isEqualByComparingTo("24.00");
        assertThat(report.kpis().billsClosedCount()).isEqualTo(3L);
        assertThat(report.waiters()).extracting(AnalyticsDtos.WaiterPerformanceRow::displayName)
                .containsExactly("Ali", "Ayse", "Atanmamış");
        assertThat(report.waiters().getFirst().revenue()).isEqualByComparingTo("150.00");
        assertThat(report.waiters().getFirst().tipAmount()).isEqualByComparingTo("10.00");
        assertThat(report.waiters().getFirst().commissionAmount()).isEqualByComparingTo("15.00");
        assertThat(report.waiters().getFirst().billsClosedCount()).isEqualTo(1L);
        assertThat(report.waiters().getFirst().topProducts()).isNotEmpty();
        assertThat(report.products()).isNotEmpty();
        assertThat(report.daily()).hasSize(1);
        assertThat(report.hourly()).hasSize(24);
    }

    @Test
    void build_whenOnlyDirectBillPayments_thenStillShowsSales() {
        Long menuId = 5L;
        LocalDate day = LocalDate.of(2026, 8, 13);
        MenuWaiter ali = waiter(101L, "Ali", true);
        TableBill bill = bill(10L, menuId, 101L);
        TableBillItem item = item(bill, 11L, "Cay");

        when(billPaymentRepository.findByMenuIdInAndPaidAtBetween(eq(List.of(menuId)), any(), any()))
                .thenReturn(List.of(
                        payment(bill, item, 101L, "75.00", 1, false, LocalDateTime.of(2026, 8, 13, 12, 0))
                ));
        when(tableBillRepository.findByMenuIdInAndStatusAndClosedAtBetween(
                eq(List.of(menuId)), eq(TableBillStatus.CLOSED), any(), any()
        )).thenReturn(List.of(bill));
        when(commissionRecordRepository.findByWaiterIdInAndCreatedAtBetween(eq(List.of(101L)), any(), any()))
                .thenReturn(List.of());

        AnalyticsDtos.MenuWaiterPerformanceReportResponse report = service.build(
                menuId,
                "Test",
                2L,
                "Kadikoy",
                List.of(menuId),
                List.of(ali),
                Map.of(101L, "Ali"),
                day,
                day
        );

        assertThat(report.kpis().totalRevenue()).isEqualByComparingTo("75.00");
        assertThat(report.waiters().getFirst().orderCount()).isEqualTo(1L);
        assertThat(report.waiters().getFirst().revenue()).isEqualByComparingTo("75.00");
    }

    private static MenuWaiter waiter(Long id, String name, boolean active) {
        return MenuWaiter.builder()
                .id(id)
                .ownerUserId(9L)
                .branchId(2L)
                .username(name.toLowerCase())
                .passwordHash("hash")
                .displayName(name)
                .active(active)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private static TableBill bill(Long id, Long menuId, Long closedByWaiterId) {
        return TableBill.builder()
                .id(id)
                .menuId(menuId)
                .tableId(1L)
                .status(TableBillStatus.CLOSED)
                .closedByWaiterId(closedByWaiterId)
                .closedAt(LocalDateTime.of(2026, 8, 13, 18, 0))
                .currency("TRY")
                .totalAmount(BigDecimal.ZERO)
                .build();
    }

    private static TableBillItem item(TableBill bill, Long productId, String name) {
        return TableBillItem.builder()
                .id(productId)
                .bill(bill)
                .productId(productId)
                .productName(name)
                .unitPrice(BigDecimal.TEN)
                .quantity(1)
                .lineTotal(BigDecimal.TEN)
                .build();
    }

    private static BillPayment payment(
            TableBill bill,
            TableBillItem item,
            Long waiterId,
            String amount,
            int quantityPaid,
            boolean tip,
            LocalDateTime paidAt
    ) {
        return BillPayment.builder()
                .bill(bill)
                .billItem(item)
                .waiterId(waiterId)
                .paymentMethod(TableBillPaymentMethod.CASH)
                .amount(new BigDecimal(amount))
                .quantityPaid(quantityPaid)
                .tip(tip)
                .paidAt(paidAt)
                .createdAt(paidAt)
                .build();
    }

    private static WaiterCommissionRecord commission(Long waiterId, Long menuId, String amount) {
        return WaiterCommissionRecord.builder()
                .waiterId(waiterId)
                .menuId(menuId)
                .branchId(2L)
                .recordType(WaiterCommissionRecordType.PERCENT_ORDER)
                .amount(new BigDecimal(amount))
                .createdAt(LocalDateTime.of(2026, 8, 13, 14, 0))
                .build();
    }
}
