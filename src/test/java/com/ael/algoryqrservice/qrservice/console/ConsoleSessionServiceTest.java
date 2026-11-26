package com.ael.algoryqrservice.qrservice.console;

import com.ael.algoryqrservice.qrservice.branch.service.BranchService;
import com.ael.algoryqrservice.qrservice.console.domain.DeviceCustomerTxnType;
import com.ael.algoryqrservice.qrservice.console.domain.DevicePaymentMethod;
import com.ael.algoryqrservice.qrservice.console.domain.DeviceType;
import com.ael.algoryqrservice.qrservice.console.dto.ConsoleDtos;
import com.ael.algoryqrservice.qrservice.console.service.ConsoleAccess;
import com.ael.algoryqrservice.qrservice.console.service.ConsoleCatalogService;
import com.ael.algoryqrservice.qrservice.console.service.ConsoleSessionFinishedEvent;
import com.ael.algoryqrservice.qrservice.console.service.ConsoleSessionService;
import com.ael.algoryqrservice.qrservice.model.Branch;
import com.ael.algoryqrservice.qrservice.model.ConsoleRateSetting;
import com.ael.algoryqrservice.qrservice.model.ConsoleSession;
import com.ael.algoryqrservice.qrservice.model.DeviceCustomer;
import com.ael.algoryqrservice.qrservice.model.DeviceCustomerTxn;
import com.ael.algoryqrservice.qrservice.model.DeviceSessionItem;
import com.ael.algoryqrservice.qrservice.model.DeviceSessionSegment;
import com.ael.algoryqrservice.qrservice.model.DeviceTypeRate;
import com.ael.algoryqrservice.qrservice.model.GameConsole;
import com.ael.algoryqrservice.qrservice.model.Menu;
import com.ael.algoryqrservice.qrservice.product.model.MenuProduct;
import com.ael.algoryqrservice.qrservice.product.repository.MenuProductRepository;
import com.ael.algoryqrservice.qrservice.repository.ConsoleRateSettingRepository;
import com.ael.algoryqrservice.qrservice.repository.ConsoleSessionRepository;
import com.ael.algoryqrservice.qrservice.repository.DeviceCustomerRepository;
import com.ael.algoryqrservice.qrservice.repository.DeviceCustomerTxnRepository;
import com.ael.algoryqrservice.qrservice.repository.DeviceReservationRepository;
import com.ael.algoryqrservice.qrservice.repository.DeviceSessionItemRepository;
import com.ael.algoryqrservice.qrservice.repository.DeviceSessionSegmentRepository;
import com.ael.algoryqrservice.qrservice.repository.DeviceTariffRuleRepository;
import com.ael.algoryqrservice.qrservice.repository.DeviceTypeRateRepository;
import com.ael.algoryqrservice.qrservice.repository.GameConsoleRepository;
import com.ael.algoryqrservice.qrservice.repository.MenuRepository;
import com.ael.algoryqrservice.qrservice.util.AppTime;
import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConsoleSessionServiceTest {

    private static final long MERCHANT = 13L;
    private static final long BRANCH = 1L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 19, 21, 30, 0);

    @Mock
    private ConsoleSessionRepository sessionRepository;
    @Mock
    private GameConsoleRepository consoleRepository;
    @Mock
    private ConsoleRateSettingRepository rateSettingRepository;
    @Mock
    private DeviceTypeRateRepository typeRateRepository;
    @Mock
    private DeviceTariffRuleRepository tariffRepository;
    @Mock
    private DeviceReservationRepository reservationRepository;
    @Mock
    private DeviceSessionSegmentRepository segmentRepository;
    @Mock
    private DeviceSessionItemRepository itemRepository;
    @Mock
    private DeviceCustomerRepository customerRepository;
    @Mock
    private DeviceCustomerTxnRepository customerTxnRepository;
    @Mock
    private MenuProductRepository menuProductRepository;
    @Mock
    private MenuRepository menuRepository;
    @Mock
    private BranchService branchService;
    @Mock
    private SecurityUtils securityUtils;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private com.ael.algoryqrservice.qrservice.repository.DeviceGameRepository gameRepository;
    @Mock
    private com.ael.algoryqrservice.qrservice.console.service.DeviceLibraryService libraryService;
    @Mock
    private com.ael.algoryqrservice.qrservice.console.service.DeviceProgramService programService;
    @Mock
    private com.ael.algoryqrservice.qrservice.console.service.DeviceCustomerService customerService;
    @Mock
    private com.ael.algoryqrservice.qrservice.repository.DeviceMembershipRepository membershipRepository;
    @Mock
    private com.ael.algoryqrservice.qrservice.repository.ConsoleSessionChangeRepository changeRepository;
    @Mock
    private com.ael.algoryqrservice.qrservice.repository.BranchRepository branchRepository;
    @Mock
    private com.ael.algoryqrservice.qrservice.console.service.ConsoleSessionAccounting accounting;

    private final List<DeviceSessionSegment> segments = new ArrayList<>();
    private final List<DeviceSessionItem> items = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong(900);

    private ConsoleCatalogService catalogService;
    private ConsoleSessionService sessionService;

    @BeforeEach
    void setUp() {
        at(NOW);
        ConsoleAccess access = new ConsoleAccess(branchService, securityUtils);
        catalogService = new ConsoleCatalogService(consoleRepository, rateSettingRepository, sessionRepository,
                typeRateRepository, tariffRepository, reservationRepository, access, gameRepository);
        sessionService = new ConsoleSessionService(sessionRepository, consoleRepository, catalogService, access,
                segmentRepository, itemRepository, customerRepository, customerTxnRepository, menuProductRepository,
                menuRepository, eventPublisher, libraryService, programService, customerService, membershipRepository,
                changeRepository, branchRepository, accounting);
        when(securityUtils.getCurrentMerchantId()).thenReturn(MERCHANT);
        when(securityUtils.findCurrentStaffId()).thenReturn(Optional.empty());
        when(sessionRepository.save(any(ConsoleSession.class))).thenAnswer(inv -> {
            ConsoleSession s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId(500L);
            }
            return s;
        });
        when(segmentRepository.save(any(DeviceSessionSegment.class))).thenAnswer(inv -> {
            DeviceSessionSegment s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId(ids.incrementAndGet());
                segments.add(s);
            }
            return s;
        });
        when(segmentRepository.findBySessionIdAndDeletedFalseOrderByStartedAtAscIdAsc(anyLong()))
                .thenAnswer(inv -> segments.stream().filter(s -> s.getSessionId().equals(inv.getArgument(0))).toList());
        when(segmentRepository.findBySessionIdInAndDeletedFalseOrderByStartedAtAscIdAsc(any()))
                .thenAnswer(inv -> segments.stream()
                        .filter(s -> ((Collection<?>) inv.getArgument(0)).contains(s.getSessionId())).toList());
        when(itemRepository.save(any(DeviceSessionItem.class))).thenAnswer(inv -> {
            DeviceSessionItem i = inv.getArgument(0);
            if (i.getId() == null) {
                i.setId(ids.incrementAndGet());
                items.add(i);
            }
            return i;
        });
        when(itemRepository.findBySessionIdAndDeletedFalseOrderByIdAsc(anyLong()))
                .thenAnswer(inv -> items.stream().filter(i -> i.getSessionId().equals(inv.getArgument(0)) && !i.isDeleted()).toList());
        when(itemRepository.findBySessionIdInAndDeletedFalseOrderByIdAsc(any()))
                .thenAnswer(inv -> items.stream()
                        .filter(i -> ((Collection<?>) inv.getArgument(0)).contains(i.getSessionId()) && !i.isDeleted()).toList());
        when(typeRateRepository.findByBranchIdAndMerchantId(BRANCH, MERCHANT)).thenReturn(List.of());
        when(tariffRepository.findByBranchIdAndMerchantIdAndDeletedFalseOrderByIdAsc(BRANCH, MERCHANT)).thenReturn(List.of());
        when(consoleRepository.findByIdIn(any())).thenReturn(List.of(console(7L, null), pc(8L)));
        when(consoleRepository.findById(7L)).thenReturn(Optional.of(console(7L, null)));
    }

    @AfterEach
    void resetClock() {
        AppTime.resetClock();
    }

    private static void at(LocalDateTime time) {
        AppTime.setClock(Clock.fixed(time.atZone(AppTime.ZONE).toInstant(), AppTime.ZONE));
    }

    // ---- start -------------------------------------------------------------------------------------------------

    @Test
    void start_usesConsoleRate_overTypeRate_overBranchDefault() {
        when(consoleRepository.lockOwned(7L, MERCHANT)).thenReturn(Optional.of(console(7L, new BigDecimal("150.00"))));
        branchRate("100.00", null);
        typeRate(DeviceType.PLAYSTATION, "130.00", null);

        ConsoleDtos.SessionResponse response = sessionService.start(
                7L, new ConsoleDtos.StartSessionRequest("  Ahmet ", " ", null, 60, null));

        assertThat(response.hourlyRate()).isEqualByComparingTo("150.00");
        assertThat(response.startedAt()).isEqualTo(NOW);
        assertThat(response.active()).isTrue();
        assertThat(response.customerName()).isEqualTo("Ahmet");
        assertThat(response.note()).isNull();
        assertThat(response.plannedMinutes()).isEqualTo(60);
        assertThat(response.controllerCount()).isEqualTo(2);
        assertThat(response.consoleName()).isEqualTo("PS5 - 1");
        assertThat(response.deviceType()).isEqualTo(DeviceType.PLAYSTATION);
        assertThat(segments).hasSize(1);
    }

    @Test
    void start_withoutConsoleRate_usesTypeRate_thenBranchDefault() {
        when(consoleRepository.lockOwned(7L, MERCHANT)).thenReturn(Optional.of(console(7L, null)));
        branchRate("100.00", null);
        assertThat(sessionService.start(7L, null).hourlyRate()).isEqualByComparingTo("100.00");

        segments.clear();
        typeRate(DeviceType.PLAYSTATION, "130.00", null);
        assertThat(sessionService.start(7L, null).hourlyRate()).isEqualByComparingTo("130.00");
    }

    @Test
    void start_withFourControllers_usesTheMultiRate() {
        when(consoleRepository.lockOwned(7L, MERCHANT)).thenReturn(Optional.of(console(7L, new BigDecimal("120.00"))));
        branchRate("100.00", "180.00");

        ConsoleDtos.SessionResponse response = sessionService.start(
                7L, new ConsoleDtos.StartSessionRequest(null, null, null, null, 4));

        assertThat(response.hourlyRate()).isEqualByComparingTo("180.00");
        assertThat(response.controllerCount()).isEqualTo(4);
    }

    @Test
    void start_withNoRateAnywhere_isBadRequest() {
        when(consoleRepository.lockOwned(7L, MERCHANT)).thenReturn(Optional.of(console(7L, null)));
        when(rateSettingRepository.findByBranchIdAndMerchantId(BRANCH, MERCHANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.start(7L, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void start_whenConsoleBusy_isConflict() {
        when(consoleRepository.lockOwned(7L, MERCHANT)).thenReturn(Optional.of(console(7L, BigDecimal.TEN)));
        when(sessionRepository.existsByConsoleIdAndEndedAtIsNullAndDeletedFalse(7L)).thenReturn(true);

        assertThatThrownBy(() -> sessionService.start(7L, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void start_onAnotherMerchantsConsole_isNotFound() {
        when(consoleRepository.lockOwned(7L, MERCHANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.start(7L, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Cihaz bulunamadı");
    }

    @Test
    void start_byStaffOfAnotherBranch_isNotFound() {
        when(securityUtils.findCurrentStaffId()).thenReturn(Optional.of(44L));
        when(securityUtils.getCurrentWaiterBranchId()).thenReturn(2L);
        when(consoleRepository.lockOwned(7L, MERCHANT)).thenReturn(Optional.of(console(7L, BigDecimal.TEN)));

        assertThatThrownBy(() -> sessionService.start(7L, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Şube bulunamadı");
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void start_byStaffOfTheBranch_recordsWhoOpenedIt() {
        when(securityUtils.findCurrentStaffId()).thenReturn(Optional.of(44L));
        when(securityUtils.getCurrentWaiterBranchId()).thenReturn(BRANCH);
        when(consoleRepository.lockOwned(7L, MERCHANT)).thenReturn(Optional.of(console(7L, BigDecimal.TEN)));

        sessionService.start(7L, null);

        ArgumentCaptor<ConsoleSession> saved = ArgumentCaptor.forClass(ConsoleSession.class);
        verify(sessionRepository).save(saved.capture());
        assertThat(saved.getValue().getOpenedByStaffId()).isEqualTo(44L);
    }

    // ---- cancel ------------------------------------------------------------------------------------------------

    @Test
    void cancel_closesTheSessionAtZeroCharge_withoutSettlingOrPosting() {
        ConsoleSession open = running(NOW.minusHours(24), "120.00");
        open.setCustomerId(77L);
        open.setPausedAt(NOW.minusHours(1));

        ConsoleDtos.SessionResponse response = sessionService.cancel(
                500L, new ConsoleDtos.CancelSessionRequest("  Açık kalmış  "));

        assertThat(open.getEndedAt()).isEqualTo(NOW);
        assertThat(open.getCancelledAt()).isEqualTo(NOW);
        assertThat(open.getCancelReason()).isEqualTo("Açık kalmış");
        assertThat(open.getPausedAt()).isNull();
        assertThat(open.getDurationMinutes()).isZero();
        assertThat(open.getAmount()).isEqualByComparingTo("0");
        assertThat(open.getGrossAmount()).isEqualByComparingTo("0");
        assertThat(open.getPaymentMethod()).isNull();
        assertThat(segments.get(0).getEndedAt()).isEqualTo(NOW);
        assertThat(response.active()).isFalse();
        assertThat(response.cancelled()).isTrue();
        assertThat(response.amount()).isEqualByComparingTo("0");
        verify(eventPublisher, never()).publishEvent(any());
        verify(customerRepository, never()).lockOwned(anyLong(), anyLong());
        verify(customerTxnRepository, never()).save(any());
    }

    @Test
    void cancel_withoutReason_leavesItEmpty_andRecordsTheStaff() {
        when(securityUtils.findCurrentStaffId()).thenReturn(Optional.of(44L));
        when(securityUtils.getCurrentWaiterBranchId()).thenReturn(BRANCH);
        ConsoleSession open = running(NOW.minusMinutes(30), "120.00");

        sessionService.cancel(500L, new ConsoleDtos.CancelSessionRequest("   "));

        assertThat(open.getCancelReason()).isNull();
        assertThat(open.getCancelledByStaffId()).isEqualTo(44L);
    }

    @Test
    void cancel_ofAFinishedSession_isConflict() {
        ConsoleSession done = session(NOW.minusHours(2), "120.00");
        done.setEndedAt(NOW.minusHours(1));
        when(sessionRepository.lockOwned(500L, MERCHANT)).thenReturn(Optional.of(done));

        assertThatThrownBy(() -> sessionService.cancel(500L, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(done.getCancelledAt()).isNull();
        verify(sessionRepository, never()).save(any());
    }

    // ---- overdue -----------------------------------------------------------------------------------------------

    @Test
    void overdue_listsFlaggedSessionsStillLate_withBranchAndMinutesPast() {
        ConsoleSession late = running(NOW.minusMinutes(100), "120.00");
        late.setPlannedMinutes(60);
        late.setOverdueAt(NOW.minusMinutes(25));
        late.setOverdueReason("PLANNED_EXCEEDED");
        Branch branch = branch();
        branch.setName("Kadıköy");
        when(branchRepository.findByMerchantIdAndDeletedFalse(MERCHANT)).thenReturn(List.of(branch));
        when(sessionRepository.findByMerchantIdAndEndedAtIsNullAndOverdueAtIsNotNullAndDeletedFalseOrderByOverdueAtAscIdAsc(MERCHANT))
                .thenReturn(List.of(late));

        List<ConsoleDtos.OverdueSessionResponse> overdue = sessionService.overdue();

        assertThat(overdue).hasSize(1);
        assertThat(overdue.get(0).reason()).isEqualTo("PLANNED_EXCEEDED");
        assertThat(overdue.get(0).overMinutes()).isEqualTo(40);
        assertThat(overdue.get(0).branchName()).isEqualTo("Kadıköy");
        assertThat(overdue.get(0).consoleName()).isEqualTo("PS5 - 1");
    }

    @Test
    void extend_clearsTheOverdueFlag() {
        ConsoleSession late = running(NOW.minusMinutes(100), "120.00");
        late.setPlannedMinutes(60);
        late.setOverdueAt(NOW.minusMinutes(25));
        late.setOverdueReason("PLANNED_EXCEEDED");

        sessionService.extend(500L, new ConsoleDtos.ExtendRequest(60));

        assertThat(late.getOverdueAt()).isNull();
        assertThat(late.getOverdueReason()).isNull();
    }

    // ---- edit --------------------------------------------------------------------------------------------------

    /** Played 5 hours at 120/h until NOW, paid 600 in cash. */
    private ConsoleSession finishedFiveHours() {
        branchRate("120.00", null);
        ConsoleSession done = running(NOW.minusHours(5), "120.00");
        segments.get(0).setEndedAt(NOW);
        done.setEndedAt(NOW);
        done.setDurationMinutes(300);
        done.setTimeAmount(new BigDecimal("600.00"));
        done.setItemsAmount(new BigDecimal("0.00"));
        done.setGrossAmount(new BigDecimal("600.00"));
        done.setDiscountAmount(new BigDecimal("0.00"));
        done.setAmount(new BigDecimal("600.00"));
        done.setPaymentMethod(DevicePaymentMethod.CASH);
        return done;
    }

    @Test
    void edit_realEndTime_rechargesUpToIt_andLogsTheChange() {
        ConsoleSession done = finishedFiveHours();

        ConsoleDtos.SessionResponse response = sessionService.edit(500L, new ConsoleDtos.EditSessionRequest(
                "Geç bitirildi", null, null, DevicePaymentMethod.CARD, NOW.minusHours(4), null, null, null));

        assertThat(done.getEndedAt()).isEqualTo(NOW.minusHours(4));
        assertThat(segments.get(0).getEndedAt()).isEqualTo(NOW.minusHours(4));
        assertThat(done.getDurationMinutes()).isEqualTo(60);
        assertThat(done.getAmount()).isEqualByComparingTo("120.00");
        assertThat(done.getPaymentMethod()).isEqualTo(DevicePaymentMethod.CARD);
        assertThat(done.getEditedAt()).isEqualTo(NOW);
        assertThat(response.amount()).isEqualByComparingTo("120.00");
        ArgumentCaptor<com.ael.algoryqrservice.qrservice.model.ConsoleSessionChange> change =
                ArgumentCaptor.forClass(com.ael.algoryqrservice.qrservice.model.ConsoleSessionChange.class);
        verify(changeRepository).save(change.capture());
        assertThat(change.getValue().getReason()).isEqualTo("Geç bitirildi");
        assertThat(change.getValue().getOldAmount()).isEqualByComparingTo("600.00");
        assertThat(change.getValue().getNewAmount()).isEqualByComparingTo("120.00");
        assertThat(change.getValue().getSummary()).contains("Ödeme: Nakit → Kart", "Tutar: 600.00 TL → 120.00 TL");
        verify(accounting).resync(done);
    }

    @Test
    void edit_addsAForgottenProduct_andAppliesADiscount() {
        ConsoleSession done = finishedFiveHours();

        sessionService.edit(500L, new ConsoleDtos.EditSessionRequest("Kola unutulmuş", null, null, null, null,
                new BigDecimal("50"), List.of(new ConsoleDtos.AddItemRequest(null, "Kola", new BigDecimal("40"), 2)), null));

        assertThat(done.getItemsAmount()).isEqualByComparingTo("80.00");
        assertThat(done.getGrossAmount()).isEqualByComparingTo("680.00");
        assertThat(done.getDiscountAmount()).isEqualByComparingTo("50.00");
        assertThat(done.getAmount()).isEqualByComparingTo("630.00");
        assertThat(done.getDurationMinutes()).isEqualTo(300);
    }

    @Test
    void edit_balancePayment_followsTheNewAmountOnTheCard() {
        ConsoleSession done = finishedFiveHours();
        done.setPaymentMethod(DevicePaymentMethod.BALANCE);
        done.setCustomerId(70L);
        DeviceCustomer customer = customer("100.00", 0, 0);
        customer.setTotalSpent(new BigDecimal("600.00"));

        sessionService.edit(500L, new ConsoleDtos.EditSessionRequest("Geç bitirildi", null, null, null,
                NOW.minusHours(4), null, null, null));

        assertThat(customer.getBalance()).isEqualByComparingTo("580.00");
        assertThat(customer.getTotalSpent()).isEqualByComparingTo("120.00");
        ArgumentCaptor<DeviceCustomerTxn> txn = ArgumentCaptor.forClass(DeviceCustomerTxn.class);
        verify(customerTxnRepository).save(txn.capture());
        assertThat(txn.getValue().getTxnType()).isEqualTo(DeviceCustomerTxnType.ADJUST);
        assertThat(txn.getValue().getAmount()).isEqualByComparingTo("480.00");
    }

    @Test
    void edit_rejectsALaterEnd_aBalanceSwitch_anOpenOrCancelledSession_andNoChange() {
        ConsoleSession done = finishedFiveHours();
        assertStatus(() -> sessionService.edit(500L, new ConsoleDtos.EditSessionRequest("x", null, null, null,
                NOW.plusHours(1), null, null, null)), HttpStatus.BAD_REQUEST);
        assertStatus(() -> sessionService.edit(500L, new ConsoleDtos.EditSessionRequest("x", null, null,
                DevicePaymentMethod.BALANCE, null, null, null, null)), HttpStatus.BAD_REQUEST);
        assertStatus(() -> sessionService.edit(500L, new ConsoleDtos.EditSessionRequest("x", null, null,
                DevicePaymentMethod.CASH, null, null, null, null)), HttpStatus.BAD_REQUEST);
        done.setCancelledAt(NOW);
        assertStatus(() -> sessionService.edit(500L, new ConsoleDtos.EditSessionRequest("x", "Ali", null, null,
                null, null, null, null)), HttpStatus.CONFLICT);
        done.setCancelledAt(null);
        done.setEndedAt(null);
        assertStatus(() -> sessionService.edit(500L, new ConsoleDtos.EditSessionRequest("x", "Ali", null, null,
                null, null, null, null)), HttpStatus.CONFLICT);
        verify(changeRepository, never()).save(any());
    }

    private static void assertStatus(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, HttpStatus status) {
        assertThatThrownBy(call)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(status));
    }

    // ---- finish ------------------------------------------------------------------------------------------------

    @Test
    void finish_storesDurationAndAmount_paidCashByDefault() {
        ConsoleSession open = running(NOW.minusMinutes(95).minusSeconds(20), "120.00");

        ConsoleDtos.SessionResponse response = sessionService.finish(500L, null);

        assertThat(open.getEndedAt()).isEqualTo(NOW);
        assertThat(open.getDurationMinutes()).isEqualTo(96);
        assertThat(open.getAmount()).isEqualByComparingTo("192.00");
        assertThat(open.getPaymentMethod()).isEqualTo(DevicePaymentMethod.CASH);
        assertThat(response.active()).isFalse();
        assertThat(response.durationMinutes()).isEqualTo(96);
        assertThat(response.amount()).isEqualByComparingTo("192.00");
        assertThat(response.consoleName()).isEqualTo("PS5 - 1");
        assertThat(segments.get(0).getEndedAt()).isEqualTo(NOW);
        verify(eventPublisher).publishEvent(new ConsoleSessionFinishedEvent(500L));
    }

    @Test
    void finish_appliesBranchRoundingAndMinimum() {
        ConsoleRateSetting setting = branchRate("120.00", null);
        setting.setRoundingMinutes(15);
        setting.setMinimumMinutes(30);
        running(NOW.minusMinutes(7), "120.00");

        ConsoleDtos.SessionResponse response = sessionService.finish(500L, null);

        assertThat(response.durationMinutes()).isEqualTo(30);
        assertThat(response.amount()).isEqualByComparingTo("60.00");
    }

    @Test
    void finish_addsItems_thenTakesThePercentDiscount() {
        running(NOW.minusMinutes(60), "120.00");
        items.add(item(500L, "Kola", "35.00", 2));

        sessionService.finish(500L, new ConsoleDtos.FinishSessionRequest(
                DevicePaymentMethod.CARD, null, new BigDecimal("10"), false));

        ConsoleSession saved = captureSaved();
        assertThat(saved.getTimeAmount()).isEqualByComparingTo("120.00");
        assertThat(saved.getItemsAmount()).isEqualByComparingTo("70.00");
        assertThat(saved.getGrossAmount()).isEqualByComparingTo("190.00");
        assertThat(saved.getDiscountAmount()).isEqualByComparingTo("19.00");
        assertThat(saved.getAmount()).isEqualByComparingTo("171.00");
        assertThat(saved.getPaymentMethod()).isEqualTo(DevicePaymentMethod.CARD);
    }

    @Test
    void finish_withBothDiscountKinds_isBadRequest() {
        running(NOW.minusMinutes(60), "120.00");

        assertThatThrownBy(() -> sessionService.finish(500L, new ConsoleDtos.FinishSessionRequest(
                null, BigDecimal.ONE, BigDecimal.ONE, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ikisi birden");
    }

    @Test
    void finish_amountDiscount_cannotGoBelowZero() {
        running(NOW.minusMinutes(30), "120.00");

        sessionService.finish(500L, new ConsoleDtos.FinishSessionRequest(null, new BigDecimal("500"), null, null));

        assertThat(captureSaved().getAmount()).isEqualByComparingTo("0");
    }

    @Test
    void finish_fromBalance_spendsIt_usesLoyaltyMinutes_andEarnsNewOnes() {
        ConsoleRateSetting setting = branchRate("120.00", null);
        setting.setLoyaltyEveryMinutes(600);
        setting.setLoyaltyRewardMinutes(60);
        ConsoleSession open = running(NOW.minusMinutes(96), "120.00");
        open.setCustomerId(70L);
        DeviceCustomer customer = customer("500.00", 30, 550);

        sessionService.finish(500L, new ConsoleDtos.FinishSessionRequest(DevicePaymentMethod.BALANCE, null, null, true));

        ConsoleSession saved = captureSaved();
        // 96 min = 192 TL; 30 free minutes are worth 60 TL.
        assertThat(saved.getLoyaltyMinutesUsed()).isEqualTo(30);
        assertThat(saved.getDiscountAmount()).isEqualByComparingTo("60.00");
        assertThat(saved.getAmount()).isEqualByComparingTo("132.00");
        assertThat(customer.getBalance()).isEqualByComparingTo("368.00");
        // 550 + 66 paid minutes = 616 -> one reward of 60, 16 carried over.
        assertThat(customer.getFreeMinutes()).isEqualTo(60);
        assertThat(customer.getLoyaltyProgressMinutes()).isEqualTo(16);
        assertThat(customer.getTotalMinutes()).isEqualTo(96);
        assertThat(customer.getTotalSpent()).isEqualByComparingTo("132.00");
        ArgumentCaptor<DeviceCustomerTxn> txn = ArgumentCaptor.forClass(DeviceCustomerTxn.class);
        verify(customerTxnRepository).save(txn.capture());
        assertThat(txn.getValue().getTxnType()).isEqualTo(DeviceCustomerTxnType.SPEND);
        assertThat(txn.getValue().getAmount()).isEqualByComparingTo("-132.00");
    }

    @Test
    void finish_fromBalance_withTooLittle_isConflict_andNothingIsSaved() {
        ConsoleSession open = running(NOW.minusMinutes(60), "120.00");
        open.setCustomerId(70L);
        customer("50.00", 0, 0);

        assertThatThrownBy(() -> sessionService.finish(500L,
                new ConsoleDtos.FinishSessionRequest(DevicePaymentMethod.BALANCE, null, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("bakiyesi yetersiz");
        verify(sessionRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void finish_fromBalance_withoutCustomer_isBadRequest() {
        running(NOW.minusMinutes(60), "120.00");

        assertThatThrownBy(() -> sessionService.finish(500L,
                new ConsoleDtos.FinishSessionRequest(DevicePaymentMethod.BALANCE, null, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("müşteri kartı");
    }

    @Test
    void finish_whenAlreadyFinished_isConflict() {
        ConsoleSession done = session(NOW.minusHours(1), "10.00");
        done.setEndedAt(NOW.minusMinutes(5));
        when(sessionRepository.lockOwned(500L, MERCHANT)).thenReturn(Optional.of(done));

        assertThatThrownBy(() -> sessionService.finish(500L, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("zaten bitmiş");
        verify(sessionRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void finish_onAnotherMerchantsSession_isNotFound() {
        when(sessionRepository.lockOwned(500L, MERCHANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.finish(500L, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Oturum bulunamadı");
    }

    // ---- pause, transfer, extend, items ------------------------------------------------------------------------

    @Test
    void pausedTime_isNotCharged() {
        running(NOW, "120.00");
        at(NOW.plusMinutes(30));
        assertThat(sessionService.pause(500L).paused()).isTrue();
        at(NOW.plusMinutes(50));
        assertThat(sessionService.resume(500L).paused()).isFalse();
        at(NOW.plusMinutes(80));

        ConsoleDtos.SessionResponse finished = sessionService.finish(500L, null);

        assertThat(finished.durationMinutes()).isEqualTo(60);
        assertThat(finished.amount()).isEqualByComparingTo("120.00");
        assertThat(finished.segments()).hasSize(2);
    }

    @Test
    void pause_twice_isConflict() {
        running(NOW.minusMinutes(5), "120.00");
        sessionService.pause(500L);

        assertThatThrownBy(() -> sessionService.pause(500L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("duraklatılmış");
    }

    @Test
    void transfer_splitsTheTime_andEachDeviceKeepsItsRate() {
        running(NOW, "120.00");
        GameConsole pc = pc(8L);
        when(consoleRepository.lockOwned(8L, MERCHANT)).thenReturn(Optional.of(pc));
        branchRate("100.00", null);
        at(NOW.plusMinutes(60));

        ConsoleDtos.SessionResponse moved = sessionService.transfer(500L, new ConsoleDtos.TransferRequest(8L, null));
        at(NOW.plusMinutes(90));
        ConsoleDtos.SessionResponse finished = sessionService.finish(500L, null);

        assertThat(moved.consoleId()).isEqualTo(8L);
        assertThat(moved.hourlyRate()).isEqualByComparingTo("40.00");
        // 60 min at 120 + 30 min at 40
        assertThat(finished.amount()).isEqualByComparingTo("140.00");
        assertThat(finished.segments()).extracting(ConsoleDtos.SessionSegmentResponse::consoleId).containsExactly(7L, 8L);
    }

    @Test
    void transfer_toABusyDevice_isConflict() {
        running(NOW, "120.00");
        when(consoleRepository.lockOwned(8L, MERCHANT)).thenReturn(Optional.of(pc(8L)));
        when(sessionRepository.existsByConsoleIdAndEndedAtIsNullAndDeletedFalse(8L)).thenReturn(true);

        assertThatThrownBy(() -> sessionService.transfer(500L, new ConsoleDtos.TransferRequest(8L, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Hedef cihazda");
    }

    @Test
    void changingControllers_switchesToTheMultiRateFromThatMoment() {
        running(NOW, "100.00");
        branchRate("100.00", "160.00");
        at(NOW.plusMinutes(30));

        sessionService.transfer(500L, new ConsoleDtos.TransferRequest(null, 4));
        at(NOW.plusMinutes(60));

        // 30 min at 100 + 30 min at 160
        assertThat(sessionService.finish(500L, null).amount()).isEqualByComparingTo("130.00");
    }

    @Test
    void extend_addsToThePlan_orStartsOneFromThePlayedTime() {
        ConsoleSession open = running(NOW.minusMinutes(25), "120.00");

        assertThat(sessionService.extend(500L, new ConsoleDtos.ExtendRequest(30)).plannedMinutes()).isEqualTo(55);
        open.setPlannedMinutes(60);
        assertThat(sessionService.extend(500L, new ConsoleDtos.ExtendRequest(30)).plannedMinutes()).isEqualTo(90);
    }

    @Test
    void addItem_fromTheBranchMenu_fixesThePrice() {
        running(NOW.minusMinutes(10), "120.00");
        MenuProduct product = new MenuProduct();
        product.setProductId(31L);
        product.setMenuId(3L);
        product.setMerchantId(MERCHANT);
        product.setName("Tost");
        product.setPrice(new BigDecimal("85.00"));
        when(menuProductRepository.findByProductIdAndDeletedFalse(31L)).thenReturn(Optional.of(product));
        Menu menu = new Menu();
        menu.setBranchId(BRANCH);
        when(menuRepository.findById(3L)).thenReturn(Optional.of(menu));

        ConsoleDtos.SessionResponse response = sessionService.addItem(500L, new ConsoleDtos.AddItemRequest(31L, null, null, 2));

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).lineTotal()).isEqualByComparingTo("170.00");
        assertThat(response.itemsAmount()).isEqualByComparingTo("170.00");
        assertThat(response.amount()).isEqualByComparingTo("190.00");
    }

    @Test
    void addItem_fromAnotherBranchMenu_isNotFound() {
        running(NOW.minusMinutes(10), "120.00");
        MenuProduct product = new MenuProduct();
        product.setProductId(31L);
        product.setMenuId(3L);
        product.setMerchantId(MERCHANT);
        product.setPrice(BigDecimal.TEN);
        when(menuProductRepository.findByProductIdAndDeletedFalse(31L)).thenReturn(Optional.of(product));
        Menu menu = new Menu();
        menu.setBranchId(99L);
        when(menuRepository.findById(3L)).thenReturn(Optional.of(menu));

        assertThatThrownBy(() -> sessionService.addItem(500L, new ConsoleDtos.AddItemRequest(31L, null, null, 1)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Ürün bulunamadı");
    }

    @Test
    void removeItem_dropsItFromTheBill() {
        running(NOW.minusMinutes(10), "120.00");
        DeviceSessionItem cola = item(500L, "Kola", "35.00", 1);
        items.add(cola);
        when(itemRepository.findByIdAndSessionIdAndDeletedFalse(cola.getId(), 500L)).thenReturn(Optional.of(cola));

        assertThat(sessionService.removeItem(500L, cola.getId()).items()).isEmpty();
    }

    // ---- reading -----------------------------------------------------------------------------------------------

    @Test
    void listActive_showsRunningTotals() {
        when(branchService.requireOwned(BRANCH)).thenReturn(branch());
        ConsoleSession open = running(NOW.minusMinutes(30), "80.00");
        when(sessionRepository.findByBranchIdAndMerchantIdAndEndedAtIsNullAndDeletedFalseOrderByStartedAtAscIdAsc(
                BRANCH, MERCHANT)).thenReturn(List.of(open));

        List<ConsoleDtos.SessionResponse> active = sessionService.listActive(BRANCH);

        assertThat(active).hasSize(1);
        assertThat(active.get(0).durationMinutes()).isEqualTo(30);
        assertThat(active.get(0).amount()).isEqualByComparingTo("40.00");
        assertThat(active.get(0).playedSeconds()).isEqualTo(1800);
        assertThat(active.get(0).endedAt()).isNull();
    }

    @Test
    void listActive_onAnotherMerchantsBranch_isNotFound() {
        when(branchService.requireOwned(99L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Şube bulunamadı"));

        assertThatThrownBy(() -> sessionService.listActive(99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Şube bulunamadı");
    }

    @Test
    void quote_showsTheLoyaltyMinutesTheCustomerCanUse() {
        ConsoleSession open = running(NOW.minusMinutes(60), "120.00");
        open.setCustomerId(70L);
        DeviceCustomer c = customer("20.00", 90, 0);
        when(sessionRepository.findById(500L)).thenReturn(Optional.of(open));
        when(customerRepository.findById(70L)).thenReturn(Optional.of(c));

        ConsoleDtos.SessionQuoteResponse quote = sessionService.quote(500L, null);

        assertThat(quote.timeAmount()).isEqualByComparingTo("120.00");
        assertThat(quote.loyaltyMinutesAvailable()).isEqualTo(90);
        assertThat(quote.loyaltyMinutesUsable()).isEqualTo(60);
        assertThat(quote.loyaltyValue()).isEqualByComparingTo("120.00");
        assertThat(quote.customerBalance()).isEqualByComparingTo("20.00");
    }

    @Test
    void deleteConsole_withOpenSession_isConflict() {
        GameConsole console = console(7L, BigDecimal.TEN);
        when(consoleRepository.findByIdAndMerchantIdAndDeletedFalse(7L, MERCHANT)).thenReturn(Optional.of(console));
        when(sessionRepository.existsByConsoleIdAndEndedAtIsNullAndDeletedFalse(7L)).thenReturn(true);

        assertThatThrownBy(() -> catalogService.delete(7L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("açık oturum");
        verify(consoleRepository, never()).save(any());
    }

    // ---- v3: packages, games, maintenance, programme, QR requests ------------------------------------------------

    @Test
    void start_withPackage_plansItsMinutes_andChargesTheFixedPrice() {
        when(consoleRepository.lockOwned(7L, MERCHANT)).thenReturn(Optional.of(console(7L, new BigDecimal("120.00"))));
        com.ael.algoryqrservice.qrservice.model.DevicePackage pkg = com.ael.algoryqrservice.qrservice.model.DevicePackage.builder()
                .id(3L).merchantId(MERCHANT).branchId(BRANCH).name("3 saat").minutes(180).price(new BigDecimal("250.00")).build();
        when(libraryService.requirePackageFor(any(), eq(3L), any())).thenReturn(pkg);

        ConsoleDtos.SessionResponse started = sessionService.start(7L,
                new ConsoleDtos.StartSessionRequest(null, null, null, null, null, 3L, null));

        assertThat(started.plannedMinutes()).isEqualTo(180);
        assertThat(started.packageName()).isEqualTo("3 saat");
        ConsoleSession saved = captureSaved();
        when(sessionRepository.lockOwned(500L, MERCHANT)).thenReturn(Optional.of(saved));
        at(NOW.plusMinutes(200));

        sessionService.finish(500L, null);

        // 180 min in the package (250) + 20 min at 120/h (40).
        assertThat(saved.getTimeAmount()).isEqualByComparingTo("290.00");
        assertThat(saved.getDurationMinutes()).isEqualTo(200);
    }

    @Test
    void start_onDeviceInMaintenance_isRefused() {
        GameConsole broken = console(7L, new BigDecimal("120.00"));
        broken.setMaintenance(true);
        when(consoleRepository.lockOwned(7L, MERCHANT)).thenReturn(Optional.of(broken));

        assertThatThrownBy(() -> sessionService.start(7L, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void start_withGame_keepsItsName() {
        when(consoleRepository.lockOwned(7L, MERCHANT)).thenReturn(Optional.of(console(7L, new BigDecimal("120.00"))));
        when(libraryService.requireGameFor(any(), eq(9L))).thenReturn(com.ael.algoryqrservice.qrservice.model.DeviceGame.builder()
                .id(9L).merchantId(MERCHANT).branchId(BRANCH).name("EA FC 26").build());

        ConsoleDtos.SessionResponse started = sessionService.start(7L,
                new ConsoleDtos.StartSessionRequest(null, null, null, null, null, null, 9L));

        assertThat(started.gameId()).isEqualTo(9L);
        assertThat(started.gameName()).isEqualTo("EA FC 26");
    }

    @Test
    void finish_takesLevelDiscount_thenBonus_andCountsTheVisit() {
        ConsoleSession open = running(NOW.minusMinutes(60), "120.00");
        open.setCustomerId(70L);
        DeviceCustomer c = customer("0.00", 0, 0);
        c.setBonusBalance(new BigDecimal("30.00"));
        c.setTotalSpent(new BigDecimal("1000.00"));
        when(programService.tiers(MERCHANT)).thenReturn(List.of(com.ael.algoryqrservice.qrservice.model.DeviceTier.builder()
                .name("Gümüş").minSpent(new BigDecimal("500.00")).discountPercent(new BigDecimal("10")).build()));

        sessionService.finish(500L, new ConsoleDtos.FinishSessionRequest(null, null, null, false, false, true));

        // 120 − 10% (12) − 30 bonus = 78.
        assertThat(open.getTierDiscountAmount()).isEqualByComparingTo("12.00");
        assertThat(open.getBonusUsed()).isEqualByComparingTo("30.00");
        assertThat(open.getDiscountAmount()).isEqualByComparingTo("42.00");
        assertThat(open.getAmount()).isEqualByComparingTo("78.00");
        assertThat(c.getBonusBalance()).isEqualByComparingTo("0.00");
        assertThat(c.getVisits()).isEqualTo(1);
        assertThat(c.getLastVisitAt()).isEqualTo(NOW);
    }

    @Test
    void finish_spendsMembershipMinutes_beforeLoyaltyMinutes() {
        ConsoleSession open = running(NOW.minusMinutes(60), "120.00");
        open.setCustomerId(70L);
        DeviceCustomer c = customer("0.00", 30, 0);
        com.ael.algoryqrservice.qrservice.model.DeviceMembership membership = com.ael.algoryqrservice.qrservice.model.DeviceMembership.builder()
                .id(44L).merchantId(MERCHANT).customerId(70L).minutesTotal(600).minutesLeft(40).build();
        when(programService.usable(eq(70L), eq(MERCHANT), any(), any())).thenReturn(List.of(membership));
        when(membershipRepository.lockById(44L)).thenReturn(Optional.of(membership));

        sessionService.finish(500L, new ConsoleDtos.FinishSessionRequest(null, null, null, true, true, false));

        // 40 membership minutes (80 TL), then 20 of the 30 loyalty minutes (40 TL): nothing left to pay.
        assertThat(open.getMembershipMinutesUsed()).isEqualTo(40);
        assertThat(open.getLoyaltyMinutesUsed()).isEqualTo(20);
        assertThat(open.getAmount()).isEqualByComparingTo("0.00");
        assertThat(open.getMembershipId()).isEqualTo(44L);
        assertThat(membership.getMinutesLeft()).isZero();
        assertThat(c.getFreeMinutes()).isEqualTo(10);
    }

    @Test
    void finish_levelDiscount_doesNotApplyToPackages() {
        ConsoleSession open = running(NOW.minusMinutes(60), "120.00");
        open.setCustomerId(70L);
        open.setPackageId(3L);
        open.setPackageMinutes(120);
        open.setPackagePrice(new BigDecimal("200.00"));
        DeviceCustomer c = customer("0.00", 0, 0);
        c.setTotalSpent(new BigDecimal("1000.00"));
        when(programService.tiers(MERCHANT)).thenReturn(List.of(com.ael.algoryqrservice.qrservice.model.DeviceTier.builder()
                .name("Gümüş").minSpent(new BigDecimal("500.00")).discountPercent(new BigDecimal("10")).build()));

        sessionService.finish(500L, null);

        assertThat(open.getAmount()).isEqualByComparingTo("200.00");
        assertThat(open.getTierDiscountAmount()).isNull();
    }

    @Test
    void finish_firstVisitOfAReferredCustomer_paysTheReferrerOnce() {
        ConsoleSession open = running(NOW.minusMinutes(30), "120.00");
        open.setCustomerId(70L);
        DeviceCustomer c = customer("0.00", 0, 0);
        c.setReferredById(71L);
        DeviceCustomer referrer = DeviceCustomer.builder().id(71L).merchantId(MERCHANT).name("Ayşe")
                .balance(BigDecimal.ZERO).totalSpent(BigDecimal.ZERO).build();
        when(customerRepository.lockOwned(71L, MERCHANT)).thenReturn(Optional.of(referrer));
        when(programService.setting(MERCHANT)).thenReturn(Optional.of(
                com.ael.algoryqrservice.qrservice.model.DeviceProgramSetting.builder()
                        .merchantId(MERCHANT).referralRewardAmount(new BigDecimal("50.00")).build()));

        sessionService.finish(500L, null);

        assertThat(c.isReferralRewarded()).isTrue();
        verify(customerService).addBonus(eq(referrer), eq(new BigDecimal("50.00")),
                eq(com.ael.algoryqrservice.qrservice.console.domain.DeviceCustomerTxnType.REFERRAL), any(), eq(500L));
    }

    @Test
    void approveExtension_addsTheRequestedMinutes_andClearsTheRequest() {
        ConsoleSession open = running(NOW.minusMinutes(50), "120.00");
        open.setPlannedMinutes(60);
        open.setExtensionRequestMinutes(30);
        open.setExtensionRequestedAt(NOW.minusMinutes(1));

        ConsoleDtos.SessionResponse response = sessionService.approveExtension(500L);

        assertThat(response.plannedMinutes()).isEqualTo(90);
        assertThat(open.getExtensionRequestMinutes()).isNull();
        assertThat(open.getExtensionRequestedAt()).isNull();
    }

    @Test
    void approveExtension_withoutARequest_isRefused() {
        running(NOW.minusMinutes(50), "120.00");

        assertThatThrownBy(() -> sessionService.approveExtension(500L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void quote_withOptions_showsTheFinalAmount() {
        ConsoleSession open = running(NOW.minusMinutes(60), "120.00");
        open.setCustomerId(70L);
        DeviceCustomer c = customer("0.00", 0, 0);
        c.setBonusBalance(new BigDecimal("20.00"));
        when(sessionRepository.findById(500L)).thenReturn(Optional.of(open));
        when(customerRepository.findById(70L)).thenReturn(Optional.of(c));

        ConsoleDtos.SessionQuoteResponse quote = sessionService.quote(500L,
                new ConsoleDtos.FinishSessionRequest(null, null, new BigDecimal("10"), false, false, true));

        // 120 − 20 bonus = 100, then 10% = 10.
        assertThat(quote.bonusUsed()).isEqualByComparingTo("20.00");
        assertThat(quote.manualDiscount()).isEqualByComparingTo("10.00");
        assertThat(quote.amount()).isEqualByComparingTo("90.00");
    }

    // ---- fixtures ----------------------------------------------------------------------------------------------

    private ConsoleSession captureSaved() {
        ArgumentCaptor<ConsoleSession> saved = ArgumentCaptor.forClass(ConsoleSession.class);
        verify(sessionRepository).save(saved.capture());
        return saved.getValue();
    }

    private ConsoleRateSetting branchRate(String rate, String multi) {
        ConsoleRateSetting setting = ConsoleRateSetting.builder()
                .id(1L)
                .merchantId(MERCHANT)
                .branchId(BRANCH)
                .defaultHourlyRate(new BigDecimal(rate))
                .defaultMultiHourlyRate(multi == null ? null : new BigDecimal(multi))
                .build();
        when(rateSettingRepository.findByBranchIdAndMerchantId(BRANCH, MERCHANT)).thenReturn(Optional.of(setting));
        return setting;
    }

    private void typeRate(DeviceType type, String rate, String multi) {
        when(typeRateRepository.findByBranchIdAndMerchantId(BRANCH, MERCHANT)).thenReturn(List.of(DeviceTypeRate.builder()
                .merchantId(MERCHANT)
                .branchId(BRANCH)
                .deviceType(type)
                .hourlyRate(new BigDecimal(rate))
                .multiHourlyRate(multi == null ? null : new BigDecimal(multi))
                .build()));
    }

    private DeviceCustomer customer(String balance, int freeMinutes, int progress) {
        DeviceCustomer customer = DeviceCustomer.builder()
                .id(70L)
                .merchantId(MERCHANT)
                .name("Ahmet")
                .balance(new BigDecimal(balance))
                .freeMinutes(freeMinutes)
                .loyaltyProgressMinutes(progress)
                .totalSpent(BigDecimal.ZERO)
                .build();
        when(customerRepository.lockOwned(eq(70L), eq(MERCHANT))).thenReturn(Optional.of(customer));
        return customer;
    }

    private DeviceSessionItem item(Long sessionId, String name, String price, int quantity) {
        BigDecimal unit = new BigDecimal(price);
        return DeviceSessionItem.builder()
                .id(ids.incrementAndGet())
                .merchantId(MERCHANT)
                .sessionId(sessionId)
                .name(name)
                .unitPrice(unit)
                .quantity(quantity)
                .lineTotal(unit.multiply(BigDecimal.valueOf(quantity)))
                .build();
    }

    /** An open session on console 7 with one stretch of play since {@code startedAt}. */
    private ConsoleSession running(LocalDateTime startedAt, String rate) {
        ConsoleSession session = session(startedAt, rate);
        when(sessionRepository.lockOwned(500L, MERCHANT)).thenReturn(Optional.of(session));
        segments.add(DeviceSessionSegment.builder()
                .id(ids.incrementAndGet())
                .sessionId(500L)
                .consoleId(7L)
                .deviceType(DeviceType.PLAYSTATION)
                .hourlyRate(new BigDecimal(rate))
                .controllerCount(2)
                .startedAt(startedAt)
                .build());
        return session;
    }

    private static ConsoleSession session(LocalDateTime startedAt, String rate) {
        return ConsoleSession.builder()
                .id(500L)
                .merchantId(MERCHANT)
                .branchId(BRANCH)
                .consoleId(7L)
                .hourlyRate(new BigDecimal(rate))
                .startedAt(startedAt)
                .build();
    }

    private static Branch branch() {
        Branch branch = new Branch();
        branch.setId(BRANCH);
        branch.setMerchantId(MERCHANT);
        return branch;
    }

    private static GameConsole console(Long id, BigDecimal hourlyRate) {
        return GameConsole.builder()
                .id(id)
                .merchantId(MERCHANT)
                .branchId(BRANCH)
                .name("PS5 - 1")
                .deviceType(DeviceType.PLAYSTATION)
                .hourlyRate(hourlyRate)
                .build();
    }

    private static GameConsole pc(Long id) {
        return GameConsole.builder()
                .id(id)
                .merchantId(MERCHANT)
                .branchId(BRANCH)
                .name("PC - 1")
                .deviceType(DeviceType.PC)
                .hourlyRate(new BigDecimal("40.00"))
                .build();
    }
}
