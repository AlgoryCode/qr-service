package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.RestaurantTable;
import com.ael.algoryqrservice.model.TableBill;
import com.ael.algoryqrservice.model.UserAccountingEntry;
import com.ael.algoryqrservice.model.dto.UserAccountingDtos;
import com.ael.algoryqrservice.model.enums.AccountingEntryType;
import com.ael.algoryqrservice.model.enums.AccountingLineType;
import com.ael.algoryqrservice.model.enums.AccountingSourceType;
import com.ael.algoryqrservice.model.enums.TableBillStatus;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.RestaurantTableRepository;
import com.ael.algoryqrservice.repository.TableBillRepository;
import com.ael.algoryqrservice.repository.UserAccountingEntryRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountingServiceTest {

    @Mock
    private UserAccountingEntryRepository userAccountingEntryRepository;
    @Mock
    private TableBillRepository tableBillRepository;
    @Mock
    private MenuRepository menuRepository;
    @Mock
    private RestaurantTableRepository restaurantTableRepository;
    @Mock
    private SecurityUtils securityUtils;

    @InjectMocks
    private UserAccountingService userAccountingService;

    @BeforeEach
    void setUp() {
        when(securityUtils.getCurrentUserId()).thenReturn(7L);
    }

    @Test
    void listForCurrentUser_whenClosedBillAndManualEntryExist_thenReturnsUnifiedLines() {
        LocalDateTime closedAt = LocalDateTime.of(2026, 8, 20, 12, 0);
        TableBill bill = TableBill.builder()
                .id(42L)
                .menuId(5L)
                .tableId(9L)
                .status(TableBillStatus.CLOSED)
                .totalAmount(new BigDecimal("150.00"))
                .tipAmount(new BigDecimal("20.00"))
                .currency("TRY")
                .closedAt(closedAt)
                .updatedAt(closedAt)
                .build();

        UserAccountingEntry manualEntry = UserAccountingEntry.builder()
                .id(11L)
                .userId(7L)
                .entryType(AccountingEntryType.GIDER)
                .title("Kira")
                .amount(new BigDecimal("5000.00"))
                .currency("TRY")
                .occurredAt(closedAt.minusDays(1))
                .sourceType(AccountingSourceType.MANUAL)
                .createdAt(closedAt.minusDays(1))
                .updatedAt(closedAt.minusDays(1))
                .build();

        when(menuRepository.findMenuIdsByUserId(7L)).thenReturn(List.of(5L));
        when(menuRepository.findById(5L)).thenReturn(Optional.of(Menu.builder()
                .menuId(5L)
                .userId(7L)
                .businessName("Test Cafe")
                .build()));
        when(tableBillRepository.findClosedBillsForMenus(
                eq(List.of(5L)),
                eq(TableBillStatus.CLOSED),
                any(),
                any()
        )).thenReturn(List.of(bill));
        when(restaurantTableRepository.findById(9L)).thenReturn(Optional.of(RestaurantTable.builder()
                .id(9L)
                .name("Masa 3")
                .build()));
        when(userAccountingEntryRepository.findAll(any(Specification.class))).thenReturn(List.of(manualEntry));

        UserAccountingDtos.EntryPageResponse response = userAccountingService.listForCurrentUser(
                "all",
                null,
                null,
                null,
                0,
                20
        );

        assertThat(response.content()).hasSize(3);
        assertThat(response.content())
                .extracting(UserAccountingDtos.LineItemResponse::id)
                .containsExactlyInAnyOrder("BILL-42", "BILL-TIP-42", "ENTRY-11");
        assertThat(response.content())
                .filteredOn(line -> line.id().equals("BILL-42"))
                .singleElement()
                .satisfies(line -> {
                    assertThat(line.type()).isEqualTo(AccountingLineType.BILL);
                    assertThat(line.title()).isEqualTo("Adisyon - Masa 3");
                });
        assertThat(response.content())
                .filteredOn(line -> line.type() == AccountingLineType.MANUAL)
                .singleElement()
                .extracting(UserAccountingDtos.LineItemResponse::entryId)
                .isEqualTo(11L);
        assertThat(response.summary().totalGelir()).isEqualByComparingTo("170.00");
        assertThat(response.summary().totalGider()).isEqualByComparingTo("5000.00");
    }

    @Test
    void listForCurrentUser_whenGiderFilter_thenSkipsBillLines() {
        when(menuRepository.findMenuIdsByUserId(7L)).thenReturn(List.of(5L));
        when(menuRepository.findById(5L)).thenReturn(Optional.of(Menu.builder()
                .menuId(5L)
                .userId(7L)
                .businessName("Test Cafe")
                .build()));
        when(userAccountingEntryRepository.findAll(any(Specification.class))).thenReturn(List.of());

        UserAccountingDtos.EntryPageResponse response = userAccountingService.listForCurrentUser(
                "GIDER",
                null,
                null,
                null,
                0,
                20
        );

        assertThat(response.content()).isEmpty();
    }
}
