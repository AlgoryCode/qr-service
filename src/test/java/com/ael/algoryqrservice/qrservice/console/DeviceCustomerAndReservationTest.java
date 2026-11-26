package com.ael.algoryqrservice.qrservice.console;

import com.ael.algoryqrservice.qrservice.branch.service.BranchService;
import com.ael.algoryqrservice.qrservice.console.domain.DeviceCustomerTxnType;
import com.ael.algoryqrservice.qrservice.console.domain.DevicePaymentMethod;
import com.ael.algoryqrservice.qrservice.console.domain.DeviceReservationStatus;
import com.ael.algoryqrservice.qrservice.console.domain.DeviceType;
import com.ael.algoryqrservice.qrservice.console.dto.ConsoleDtos;
import com.ael.algoryqrservice.qrservice.console.service.ConsoleAccess;
import com.ael.algoryqrservice.qrservice.console.service.ConsoleSessionService;
import com.ael.algoryqrservice.qrservice.console.service.DeviceCustomerService;
import com.ael.algoryqrservice.qrservice.console.service.DeviceProgramService;
import com.ael.algoryqrservice.qrservice.console.service.DeviceReportService;
import com.ael.algoryqrservice.qrservice.console.service.DeviceReservationService;
import com.ael.algoryqrservice.qrservice.model.Branch;
import com.ael.algoryqrservice.qrservice.model.ConsoleSession;
import com.ael.algoryqrservice.qrservice.model.DeviceCustomer;
import com.ael.algoryqrservice.qrservice.model.DeviceCustomerTxn;
import com.ael.algoryqrservice.qrservice.model.DeviceReservation;
import com.ael.algoryqrservice.qrservice.model.DeviceSessionSegment;
import com.ael.algoryqrservice.qrservice.model.GameConsole;
import com.ael.algoryqrservice.qrservice.repository.ConsoleSessionRepository;
import com.ael.algoryqrservice.qrservice.repository.DeviceCustomerRepository;
import com.ael.algoryqrservice.qrservice.repository.DeviceCustomerTxnRepository;
import com.ael.algoryqrservice.qrservice.repository.DeviceReservationRepository;
import com.ael.algoryqrservice.qrservice.repository.DeviceSessionSegmentRepository;
import com.ael.algoryqrservice.qrservice.repository.GameConsoleRepository;
import com.ael.algoryqrservice.qrservice.util.AppTime;
import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeviceCustomerAndReservationTest {

    private static final long MERCHANT = 13L;
    private static final long BRANCH = 1L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 18, 0);

    @Mock
    private DeviceCustomerRepository customerRepository;
    @Mock
    private DeviceCustomerTxnRepository txnRepository;
    @Mock
    private DeviceReservationRepository reservationRepository;
    @Mock
    private GameConsoleRepository consoleRepository;
    @Mock
    private ConsoleSessionRepository sessionRepository;
    @Mock
    private DeviceSessionSegmentRepository segmentRepository;
    @Mock
    private ConsoleSessionService sessionService;
    @Mock
    private BranchService branchService;
    @Mock
    private SecurityUtils securityUtils;

    private DeviceCustomerService customerService;
    private DeviceProgramService programService;
    @Mock
    private com.ael.algoryqrservice.qrservice.repository.DeviceProgramSettingRepository settingRepository;
    @Mock
    private com.ael.algoryqrservice.qrservice.repository.DeviceTierRepository tierRepository;
    @Mock
    private com.ael.algoryqrservice.qrservice.repository.DeviceMembershipPlanRepository planRepository;
    @Mock
    private com.ael.algoryqrservice.qrservice.repository.DeviceMembershipRepository membershipRepository;
    @Mock
    private com.ael.algoryqrservice.qrservice.console.service.DeviceSaleAccounting saleAccounting;
    @Mock
    private com.ael.algoryqrservice.qrservice.repository.DeviceMaintenanceLogRepository maintenanceRepository;
    private DeviceReservationService reservationService;
    private DeviceReportService reportService;

    @BeforeEach
    void setUp() {
        AppTime.setClock(Clock.fixed(NOW.atZone(AppTime.ZONE).toInstant(), AppTime.ZONE));
        ConsoleAccess access = new ConsoleAccess(branchService, securityUtils);
        programService = new DeviceProgramService(settingRepository, tierRepository, planRepository, membershipRepository,
                customerRepository, txnRepository, saleAccounting, access);
        customerService = new DeviceCustomerService(customerRepository, txnRepository, access, programService);
        reservationService = new DeviceReservationService(
                reservationRepository, consoleRepository, customerRepository, sessionService, access);
        reportService = new DeviceReportService(sessionRepository, segmentRepository, consoleRepository, access,
                maintenanceRepository);
        when(securityUtils.getCurrentMerchantId()).thenReturn(MERCHANT);
        when(securityUtils.findCurrentStaffId()).thenReturn(Optional.empty());
        Branch branch = new Branch();
        branch.setId(BRANCH);
        branch.setMerchantId(MERCHANT);
        when(branchService.requireOwned(BRANCH)).thenReturn(branch);
        when(customerRepository.save(any(DeviceCustomer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reservationRepository.save(any(DeviceReservation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(consoleRepository.lockOwned(7L, MERCHANT)).thenReturn(Optional.of(console()));
    }

    @AfterEach
    void resetClock() {
        AppTime.resetClock();
    }

    // ---- customers ---------------------------------------------------------------------------------------------

    @Test
    void create_withAPhoneAlreadyUsed_isConflict() {
        when(customerRepository.existsByMerchantIdAndPhoneAndDeletedFalse(MERCHANT, "05321112233")).thenReturn(true);

        assertThatThrownBy(() -> customerService.create(new ConsoleDtos.CustomerRequest("Ali", "0532 111 22 33", null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("telefonla");
    }

    @Test
    void create_withAFriendsCode_givesTheWelcomeBonus_andRemembersTheReferrer() {
        DeviceCustomer friend = DeviceCustomer.builder().id(71L).merchantId(MERCHANT).name("Ayşe")
                .balance(BigDecimal.ZERO).totalSpent(BigDecimal.ZERO).referralCode("ABC234").build();
        when(customerRepository.findByMerchantIdAndReferralCodeAndDeletedFalse(MERCHANT, "ABC234")).thenReturn(Optional.of(friend));
        when(settingRepository.findByMerchantId(MERCHANT)).thenReturn(Optional.of(
                com.ael.algoryqrservice.qrservice.model.DeviceProgramSetting.builder()
                        .merchantId(MERCHANT).referralWelcomeAmount(new BigDecimal("25.00")).build()));

        ConsoleDtos.CustomerResponse created = customerService.create(
                new ConsoleDtos.CustomerRequest("Mert", null, null, " abc234 "));

        assertThat(created.referredById()).isEqualTo(71L);
        assertThat(created.bonusBalance()).isEqualByComparingTo("25.00");
        assertThat(created.balance()).isEqualByComparingTo("0.00");
        assertThat(created.referralCode()).hasSize(6);
    }

    @Test
    void create_withAnUnknownCode_isBadRequest() {
        assertThatThrownBy(() -> customerService.create(new ConsoleDtos.CustomerRequest("Mert", null, null, "NOPE")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void gift_goesToTheBonusBalance_notThePaidBalance() {
        DeviceCustomer customer = customer("20.00");

        ConsoleDtos.CustomerResponse gifted = customerService.gift(customer.getId(),
                new ConsoleDtos.GiftRequest(new BigDecimal("40"), "Doğum günü"));

        assertThat(gifted.balance()).isEqualByComparingTo("20.00");
        assertThat(gifted.bonusBalance()).isEqualByComparingTo("40.00");
        ArgumentCaptor<DeviceCustomerTxn> txn = ArgumentCaptor.forClass(DeviceCustomerTxn.class);
        verify(txnRepository).save(txn.capture());
        assertThat(txn.getValue().getTxnType()).isEqualTo(com.ael.algoryqrservice.qrservice.console.domain.DeviceCustomerTxnType.GIFT);
    }

    @Test
    void customer_showsTheLevelReached() {
        DeviceCustomer customer = customer("0.00");
        customer.setTotalSpent(new BigDecimal("750.00"));
        when(customerRepository.findByIdAndMerchantIdAndDeletedFalse(70L, MERCHANT)).thenReturn(Optional.of(customer));
        when(tierRepository.findByMerchantIdAndDeletedFalseOrderByMinSpentAscIdAsc(MERCHANT)).thenReturn(List.of(
                com.ael.algoryqrservice.qrservice.model.DeviceTier.builder().name("Bronz").minSpent(new BigDecimal("0")).discountPercent(BigDecimal.ZERO).build(),
                com.ael.algoryqrservice.qrservice.model.DeviceTier.builder().name("Gümüş").minSpent(new BigDecimal("500")).discountPercent(new BigDecimal("5")).build(),
                com.ael.algoryqrservice.qrservice.model.DeviceTier.builder().name("Altın").minSpent(new BigDecimal("2000")).discountPercent(new BigDecimal("10")).build()));

        ConsoleDtos.CustomerResponse response = customerService.get(customer.getId());

        assertThat(response.tierName()).isEqualTo("Gümüş");
        assertThat(response.tierDiscountPercent()).isEqualByComparingTo("5");
    }

    @Test
    void topUp_addsBalance_andLogsIt() {
        DeviceCustomer customer = customer("20.00");

        ConsoleDtos.CustomerResponse response = customerService.topUp(70L,
                new ConsoleDtos.TopUpRequest(new BigDecimal("200"), DevicePaymentMethod.CARD, null));

        assertThat(response.balance()).isEqualByComparingTo("220.00");
        assertThat(customer.getBalance()).isEqualByComparingTo("220.00");
        ArgumentCaptor<DeviceCustomerTxn> txn = ArgumentCaptor.forClass(DeviceCustomerTxn.class);
        verify(txnRepository).save(txn.capture());
        assertThat(txn.getValue().getTxnType()).isEqualTo(DeviceCustomerTxnType.TOPUP);
        assertThat(txn.getValue().getPaymentMethod()).isEqualTo(DevicePaymentMethod.CARD);
    }

    @Test
    void topUp_fromBalance_isBadRequest() {
        customer("20.00");

        assertThatThrownBy(() -> customerService.topUp(70L,
                new ConsoleDtos.TopUpRequest(BigDecimal.TEN, DevicePaymentMethod.BALANCE, null)))
                .isInstanceOf(ResponseStatusException.class);
        verify(txnRepository, never()).save(any());
    }

    @Test
    void adjust_cannotTakeTheBalanceBelowZero() {
        customer("20.00");

        assertThatThrownBy(() -> customerService.adjust(70L, new ConsoleDtos.AdjustRequest(new BigDecimal("-30"), null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("eksiye");
    }

    @Test
    void adjust_canGiveFreeMinutes() {
        DeviceCustomer customer = customer("0.00");

        customerService.adjust(70L, new ConsoleDtos.AdjustRequest(null, 60, "Doğum günü"));

        assertThat(customer.getFreeMinutes()).isEqualTo(60);
    }

    @Test
    void delete_withBalanceLeft_isConflict() {
        DeviceCustomer customer = customer("5.00");
        when(customerRepository.findByIdAndMerchantIdAndDeletedFalse(70L, MERCHANT)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> customerService.delete(70L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("bakiyesi var");
    }

    // ---- reservations ------------------------------------------------------------------------------------------

    @Test
    void reserve_overlappingABooking_isConflict() {
        when(reservationRepository.existsOverlap(eq(7L), eq(DeviceReservationStatus.BOOKED), any(), any(), eq(-1L)))
                .thenReturn(true);

        assertThatThrownBy(() -> reservationService.create(BRANCH, request(NOW.plusHours(2), 60)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("rezerve");
    }

    @Test
    void reserve_inThePast_isBadRequest() {
        assertThatThrownBy(() -> reservationService.create(BRANCH, request(NOW.minusHours(3), 60)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Geçmiş");
    }

    @Test
    void reserve_setsTheEndFromTheLength() {
        ConsoleDtos.ReservationResponse response = reservationService.create(BRANCH, request(NOW.plusHours(2), 90));

        assertThat(response.startsAt()).isEqualTo(NOW.plusHours(2));
        assertThat(response.endsAt()).isEqualTo(NOW.plusHours(2).plusMinutes(90));
        assertThat(response.status()).isEqualTo(DeviceReservationStatus.BOOKED);
        assertThat(response.customerName()).isEqualTo("Zeynep");
    }

    @Test
    void start_opensASessionPlannedForTheReservedLength_andMarksItStarted() {
        DeviceReservation reservation = DeviceReservation.builder()
                .id(40L).merchantId(MERCHANT).branchId(BRANCH).consoleId(7L).customerName("Zeynep")
                .startsAt(NOW).endsAt(NOW.plusMinutes(90)).status(DeviceReservationStatus.BOOKED).build();
        when(reservationRepository.lockOwned(40L, MERCHANT)).thenReturn(Optional.of(reservation));
        when(sessionService.startOn(any(), any(), eq(40L))).thenAnswer(inv -> {
            ConsoleDtos.StartSessionRequest req = inv.getArgument(1);
            assertThat(req.plannedMinutes()).isEqualTo(90);
            assertThat(req.customerName()).isEqualTo("Zeynep");
            return sessionResponse(600L);
        });

        reservationService.start(40L, null);

        assertThat(reservation.getStatus()).isEqualTo(DeviceReservationStatus.STARTED);
        assertThat(reservation.getSessionId()).isEqualTo(600L);
    }

    @Test
    void cancel_aStartedReservation_isConflict() {
        DeviceReservation reservation = DeviceReservation.builder()
                .id(40L).merchantId(MERCHANT).branchId(BRANCH).consoleId(7L).customerName("Zeynep")
                .startsAt(NOW).endsAt(NOW.plusMinutes(90)).status(DeviceReservationStatus.STARTED).build();
        when(reservationRepository.lockOwned(40L, MERCHANT)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.cancel(40L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("açık değil");
    }

    // ---- report ------------------------------------------------------------------------------------------------

    @Test
    void report_splitsRevenueByPlayedTime_andFillsHoursAndPayments() {
        GameConsole ps = console();
        GameConsole pc = GameConsole.builder().id(8L).merchantId(MERCHANT).branchId(BRANCH).name("PC - 1")
                .deviceType(DeviceType.PC).build();
        when(consoleRepository.findByBranchIdAndMerchantIdAndDeletedFalseOrderByNameAscIdAsc(BRANCH, MERCHANT))
                .thenReturn(List.of(pc, ps));
        LocalDateTime start = NOW.minusHours(3);
        ConsoleSession session = ConsoleSession.builder().id(500L).merchantId(MERCHANT).branchId(BRANCH).consoleId(8L)
                .startedAt(start).endedAt(start.plusMinutes(90)).durationMinutes(90)
                .timeAmount(new BigDecimal("140.00")).itemsAmount(new BigDecimal("30.00"))
                .discountAmount(new BigDecimal("10.00")).amount(new BigDecimal("160.00"))
                .paymentMethod(DevicePaymentMethod.CARD).build();
        when(sessionRepository.findFinishedForBranch(eq(MERCHANT), eq(BRANCH), any(), any())).thenReturn(List.of(session));
        when(segmentRepository.findBySessionIdInAndDeletedFalseOrderByStartedAtAscIdAsc(any())).thenReturn(List.of(
                segment(7L, start, start.plusMinutes(60)),
                segment(8L, start.plusMinutes(60), start.plusMinutes(90))));

        ConsoleDtos.ReportResponse report = reportService.report(BRANCH, NOW.toLocalDate(), NOW.toLocalDate());

        assertThat(report.sessions()).isEqualTo(1);
        assertThat(report.revenue()).isEqualByComparingTo("160.00");
        assertThat(report.itemsRevenue()).isEqualByComparingTo("30.00");
        assertThat(report.discounts()).isEqualByComparingTo("10.00");
        assertThat(report.playedMinutes()).isEqualTo(90);
        ConsoleDtos.DeviceReportRow psRow = report.devices().stream().filter(d -> d.consoleId() == 7L).findFirst().orElseThrow();
        ConsoleDtos.DeviceReportRow pcRow = report.devices().stream().filter(d -> d.consoleId() == 8L).findFirst().orElseThrow();
        assertThat(psRow.revenue()).isEqualByComparingTo("106.67");
        assertThat(pcRow.revenue()).isEqualByComparingTo("53.33");
        assertThat(pcRow.sessions()).isEqualTo(1);
        assertThat(psRow.playedMinutes()).isEqualTo(60);
        // 15:00-16:30 -> 60 min in hour 15, 30 in hour 16
        assertThat(report.hours().get(15).playedMinutes()).isEqualTo(60);
        assertThat(report.hours().get(16).playedMinutes()).isEqualTo(30);
        assertThat(report.hours().get(15).sessionsStarted()).isEqualTo(1);
        assertThat(report.payments()).singleElement()
                .satisfies(p -> assertThat(p.paymentMethod()).isEqualTo(DevicePaymentMethod.CARD));
        // 60 of the 1080 minutes since midnight
        assertThat(psRow.occupancyPercent()).isEqualByComparingTo("5.6");
    }

    // ---- fixtures ----------------------------------------------------------------------------------------------

    private DeviceCustomer customer(String balance) {
        DeviceCustomer customer = DeviceCustomer.builder()
                .id(70L).merchantId(MERCHANT).name("Ahmet")
                .balance(new BigDecimal(balance)).totalSpent(BigDecimal.ZERO).build();
        when(customerRepository.lockOwned(70L, MERCHANT)).thenReturn(Optional.of(customer));
        return customer;
    }

    private static ConsoleDtos.ReservationRequest request(LocalDateTime startsAt, int minutes) {
        return new ConsoleDtos.ReservationRequest(7L, null, " Zeynep ", null, null, startsAt, minutes);
    }

    private static GameConsole console() {
        return GameConsole.builder().id(7L).merchantId(MERCHANT).branchId(BRANCH).name("PS5 - 1")
                .deviceType(DeviceType.PLAYSTATION).build();
    }

    private static DeviceSessionSegment segment(Long consoleId, LocalDateTime start, LocalDateTime end) {
        return DeviceSessionSegment.builder().sessionId(500L).consoleId(consoleId).deviceType(DeviceType.PLAYSTATION)
                .hourlyRate(BigDecimal.TEN).controllerCount(2).startedAt(start).endedAt(end).build();
    }

    private static ConsoleDtos.SessionResponse sessionResponse(Long id) {
        return new ConsoleDtos.SessionResponse(id, BRANCH, 7L, "PS5 - 1", DeviceType.PLAYSTATION, null, "Zeynep", null,
                BigDecimal.TEN, 2, NOW, null, null, false, 90, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, null, BigDecimal.ZERO, null, true, 40L, List.of(), List.of(),
                null, null, null, null, null, null, null, null, null, null, null, false, null, null, null, null, null);
    }

}
