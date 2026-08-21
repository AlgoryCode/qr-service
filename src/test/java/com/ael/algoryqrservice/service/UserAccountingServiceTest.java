package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.UserAccountingEntry;
import com.ael.algoryqrservice.model.dto.UserAccountingDtos;
import com.ael.algoryqrservice.model.enums.AccountingEntryType;
import com.ael.algoryqrservice.model.enums.AccountingSourceType;
import com.ael.algoryqrservice.repository.MenuOrderItemRepository;
import com.ael.algoryqrservice.repository.MenuOrderRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.RestaurantTableRepository;
import com.ael.algoryqrservice.repository.TableBillItemRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountingServiceTest {

    @Mock
    private UserAccountingEntryRepository userAccountingEntryRepository;
    @Mock
    private TableBillRepository tableBillRepository;
    @Mock
    private TableBillItemRepository tableBillItemRepository;
    @Mock
    private MenuRepository menuRepository;
    @Mock
    private RestaurantTableRepository restaurantTableRepository;
    @Mock
    private MenuOrderRepository menuOrderRepository;
    @Mock
    private MenuOrderItemRepository menuOrderItemRepository;
    @Mock
    private SecurityUtils securityUtils;

    @InjectMocks
    private UserAccountingService userAccountingService;

    @BeforeEach
    void setUp() {
        when(securityUtils.getCurrentUserId()).thenReturn(7L);
    }

    @Test
    void listForCurrentUser_whenBillSaleAndManualExist_thenReturnsStoredLinesWithoutTip() {
        LocalDateTime closedAt = LocalDateTime.of(2026, 8, 20, 12, 0);

        UserAccountingEntry billSale = UserAccountingEntry.builder()
                .id(42L)
                .userId(7L)
                .entryType(AccountingEntryType.GELIR)
                .title("Adisyon - Masa 3")
                .amount(new BigDecimal("150.00"))
                .currency("TRY")
                .occurredAt(closedAt)
                .menuId(5L)
                .sourceType(AccountingSourceType.BILL_SALE)
                .sourceBillId(42L)
                .sourceOrderId(99L)
                .createdAt(closedAt)
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
        when(menuRepository.findById(5L)).thenReturn(Optional.of(com.ael.algoryqrservice.model.Menu.builder()
                .menuId(5L)
                .userId(7L)
                .businessName("Test Cafe")
                .build()));
        when(userAccountingEntryRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(billSale, manualEntry));

        UserAccountingDtos.EntryPageResponse response = userAccountingService.listForCurrentUser(
                "all",
                null,
                null,
                null,
                0,
                20
        );

        assertThat(response.content()).hasSize(2);
        assertThat(response.content())
                .extracting(UserAccountingDtos.EntryResponse::id)
                .containsExactlyInAnyOrder(42L, 11L);
        assertThat(response.content())
                .filteredOn(line -> line.sourceType() == AccountingSourceType.BILL_SALE)
                .singleElement()
                .satisfies(line -> {
                    assertThat(line.title()).isEqualTo("Adisyon - Masa 3");
                    assertThat(line.sourceOrderId()).isEqualTo(99L);
                    assertThat(line.menuName()).isEqualTo("Test Cafe");
                });
        assertThat(response.summary().totalGelir()).isEqualByComparingTo("150.00");
        assertThat(response.summary().totalGider()).isEqualByComparingTo("5000.00");
    }

    @Test
    void listForCurrentUser_whenGiderFilter_thenReturnsOnlyMatching() {
        when(menuRepository.findMenuIdsByUserId(7L)).thenReturn(List.of(5L));
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
