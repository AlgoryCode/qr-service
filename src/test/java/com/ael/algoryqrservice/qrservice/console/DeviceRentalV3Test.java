package com.ael.algoryqrservice.qrservice.console;

import com.ael.algoryqrservice.qrservice.branch.service.BranchService;
import com.ael.algoryqrservice.qrservice.console.domain.DeviceCheckout;
import com.ael.algoryqrservice.qrservice.console.domain.DevicePaymentMethod;
import com.ael.algoryqrservice.qrservice.console.domain.DeviceRentalPricing;
import com.ael.algoryqrservice.qrservice.console.domain.DeviceTournamentStatus;
import com.ael.algoryqrservice.qrservice.console.domain.DeviceType;
import com.ael.algoryqrservice.qrservice.console.dto.ConsoleDtos;
import com.ael.algoryqrservice.qrservice.console.service.ConsoleAccess;
import com.ael.algoryqrservice.qrservice.console.service.ConsoleCatalogService;
import com.ael.algoryqrservice.qrservice.console.service.ConsoleSessionService;
import com.ael.algoryqrservice.qrservice.console.service.DevicePublicService;
import com.ael.algoryqrservice.qrservice.console.service.DeviceSaleAccounting;
import com.ael.algoryqrservice.qrservice.console.service.DeviceShiftService;
import com.ael.algoryqrservice.qrservice.console.service.DeviceTournamentService;
import com.ael.algoryqrservice.qrservice.model.Branch;
import com.ael.algoryqrservice.qrservice.model.ConsoleSession;
import com.ael.algoryqrservice.qrservice.model.DeviceCustomerTxn;
import com.ael.algoryqrservice.qrservice.model.DeviceShift;
import com.ael.algoryqrservice.qrservice.model.DeviceTournament;
import com.ael.algoryqrservice.qrservice.model.DeviceTournamentMatch;
import com.ael.algoryqrservice.qrservice.model.DeviceTournamentPlayer;
import com.ael.algoryqrservice.qrservice.model.GameConsole;
import com.ael.algoryqrservice.qrservice.repository.BranchRepository;
import com.ael.algoryqrservice.qrservice.repository.ConsoleSessionRepository;
import com.ael.algoryqrservice.qrservice.repository.DeviceCustomerRepository;
import com.ael.algoryqrservice.qrservice.repository.DeviceCustomerTxnRepository;
import com.ael.algoryqrservice.qrservice.repository.DeviceMembershipRepository;
import com.ael.algoryqrservice.qrservice.repository.DeviceShiftRepository;
import com.ael.algoryqrservice.qrservice.repository.DeviceTournamentMatchRepository;
import com.ael.algoryqrservice.qrservice.repository.DeviceTournamentPlayerRepository;
import com.ael.algoryqrservice.qrservice.repository.DeviceTournamentRepository;
import com.ael.algoryqrservice.qrservice.repository.GameConsoleRepository;
import com.ael.algoryqrservice.qrservice.security.ProductUsageGateway;
import com.ael.algoryqrservice.qrservice.util.AppTime;
import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
import com.ael.algoryqrservice.staffservice.infrastructure.persistence.repository.MerchantStaffRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceRentalV3Test {

    private static final long MERCHANT = 13L;
    private static final long BRANCH = 1L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 20, 0);

    private static BigDecimal tl(String value) {
        return new BigDecimal(value);
    }

    // ---- Checkout order --------------------------------------------------------------------------------------

    @Nested
    class Checkout {

        @Test
        void noCustomerNoOptions_paysTheGross() {
            DeviceCheckout.Result r = DeviceCheckout.compute(tl("100.00"), 60, tl("30.00"), false, null, null);
            assertThat(r.grossAmount()).isEqualByComparingTo("130.00");
            assertThat(r.totalDiscount()).isEqualByComparingTo("0.00");
            assertThat(r.amount()).isEqualByComparingTo("130.00");
        }

        @Test
        void levelDiscount_isOnTimeLeftAfterFreeMinutes_notOnItems() {
            DeviceCheckout.Customer customer = new DeviceCheckout.Customer(0, 30, tl("10"), null);
            DeviceCheckout.Options options = new DeviceCheckout.Options(false, true, false, null, null);

            DeviceCheckout.Result r = DeviceCheckout.compute(tl("120.00"), 60, tl("50.00"), false, customer, options);

            // 30 of 60 min free = 60; 10% of the remaining 60 time = 6; items untouched.
            assertThat(r.loyaltyValue()).isEqualByComparingTo("60.00");
            assertThat(r.tierDiscount()).isEqualByComparingTo("6.00");
            assertThat(r.amount()).isEqualByComparingTo("104.00");
        }

        @Test
        void bonus_isCappedAtWhatIsLeft_andManualDiscountComesLast() {
            DeviceCheckout.Customer customer = new DeviceCheckout.Customer(0, 0, null, tl("500.00"));
            DeviceCheckout.Options options = new DeviceCheckout.Options(false, false, true, tl("10.00"), null);

            DeviceCheckout.Result r = DeviceCheckout.compute(tl("80.00"), 40, tl("0"), false, customer, options);

            assertThat(r.bonusUsed()).isEqualByComparingTo("80.00");
            assertThat(r.manualDiscount()).isEqualByComparingTo("0.00");
            assertThat(r.amount()).isEqualByComparingTo("0.00");
        }

        @Test
        void freeMinutesTogether_neverExceedTheTimeAmount() {
            DeviceCheckout.Customer customer = new DeviceCheckout.Customer(25, 25, null, null);
            DeviceCheckout.Options options = new DeviceCheckout.Options(true, true, false, null, null);

            DeviceCheckout.Result r = DeviceCheckout.compute(tl("33.33"), 45, tl("0"), false, customer, options);

            assertThat(r.membershipMinutesUsed()).isEqualTo(25);
            assertThat(r.loyaltyMinutesUsed()).isEqualTo(20);
            assertThat(r.membershipValue().add(r.loyaltyValue())).isLessThanOrEqualTo(tl("33.33"));
            assertThat(r.amount()).isEqualByComparingTo("0.00");
        }

        @Test
        void unusedOptions_takeNothing() {
            DeviceCheckout.Customer customer = new DeviceCheckout.Customer(100, 100, null, tl("100"));

            DeviceCheckout.Result r = DeviceCheckout.compute(tl("50.00"), 30, tl("0"), false, customer, DeviceCheckout.Options.NONE);

            assertThat(r.amount()).isEqualByComparingTo("50.00");
            assertThat(r.membershipMinutesUsed()).isZero();
            assertThat(r.loyaltyMinutesUsed()).isZero();
        }
    }

    // ---- Package pricing -------------------------------------------------------------------------------------

    @Nested
    class PackagePricing {

        private DeviceRentalPricing.Segment seg(LocalDateTime start, LocalDateTime end, String rate) {
            return new DeviceRentalPricing.Segment(start, end, tl(rate), DeviceType.PLAYSTATION);
        }

        @Test
        void withinThePackage_costsThePackagePrice() {
            var quote = DeviceRentalPricing.quoteWithPackage(
                    List.of(seg(NOW.minusMinutes(50), null, "120")), DeviceRentalPricing.Rules.NONE, NOW, 180, tl("250"));
            assertThat(quote.amount()).isEqualByComparingTo("250.00");
            assertThat(quote.billedMinutes()).isEqualTo(180);
            assertThat(quote.playedSeconds()).isEqualTo(50 * 60);
        }

        @Test
        void beyondThePackage_isChargedPerSegment_withRounding_butNoMinimum() {
            // 60 + 15 min played with a pause; a 60-min package covers the first stretch exactly.
            List<DeviceRentalPricing.Segment> segments = List.of(
                    seg(NOW.minusMinutes(100), NOW.minusMinutes(40), "120"),
                    seg(NOW.minusMinutes(15), null, "60"));
            var rules = new DeviceRentalPricing.Rules(120, 30, List.of());

            var quote = DeviceRentalPricing.quoteWithPackage(segments, rules, NOW, 60, tl("100"));

            // 15 min beyond, rounded to 30 at the 60/h rate = 30.
            assertThat(quote.amount()).isEqualByComparingTo("130.00");
            assertThat(quote.billedMinutes()).isEqualTo(90);
        }

        @Test
        void packageEndingMidSegment_chargesOnlyTheRest() {
            var quote = DeviceRentalPricing.quoteWithPackage(
                    List.of(seg(NOW.minusMinutes(90), null, "120")), DeviceRentalPricing.Rules.NONE, NOW, 60, tl("100"));
            assertThat(quote.amount()).isEqualByComparingTo("160.00");
        }

        @Test
        void noPackage_isTheNormalQuote() {
            var segments = List.of(seg(NOW.minusMinutes(30), null, "120"));
            assertThat(DeviceRentalPricing.quoteWithPackage(segments, DeviceRentalPricing.Rules.NONE, NOW, null, null))
                    .isEqualTo(DeviceRentalPricing.quote(segments, DeviceRentalPricing.Rules.NONE, NOW));
        }
    }

    // ---- Tournament bracket ----------------------------------------------------------------------------------

    @Nested
    class Bracket {

        @SuppressWarnings("unchecked")
        private List<DeviceTournamentMatch> draw(List<Long> players) throws Exception {
            Method draw = DeviceTournamentService.class.getDeclaredMethod("draw", Long.class, List.class);
            draw.setAccessible(true);
            return (List<DeviceTournamentMatch>) draw.invoke(null, 5L, players);
        }

        @Test
        void fivePlayers_getAnEightSlotBracket_withThreeByes() throws Exception {
            List<DeviceTournamentMatch> matches = draw(List.of(1L, 2L, 3L, 4L, 5L));

            List<DeviceTournamentMatch> first = matches.stream().filter(m -> m.getRound() == 1).toList();
            assertThat(first).hasSize(4);
            assertThat(matches.stream().filter(m -> m.getRound() == 2)).hasSize(2);
            assertThat(matches.stream().filter(m -> m.getRound() == 3)).hasSize(1);
            assertThat(first.stream().filter(m -> m.getPlayer2Id() == null)).hasSize(3);
            assertThat(first).allMatch(m -> m.getPlayer1Id() != null);
            Set<Long> seen = new HashSet<>();
            first.forEach(m -> {
                seen.add(m.getPlayer1Id());
                if (m.getPlayer2Id() != null) {
                    seen.add(m.getPlayer2Id());
                }
            });
            assertThat(seen).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L);
        }

        @Test
        void powerOfTwo_hasNoByes() throws Exception {
            List<DeviceTournamentMatch> first = draw(List.of(1L, 2L, 3L, 4L)).stream().filter(m -> m.getRound() == 1).toList();
            assertThat(first).hasSize(2).allMatch(m -> m.getPlayer1Id() != null && m.getPlayer2Id() != null);
        }

        @Test
        void twoPlayers_playOneFinal() throws Exception {
            List<DeviceTournamentMatch> matches = draw(List.of(1L, 2L));
            assertThat(matches).hasSize(1);
            assertThat(matches.get(0).getPlayer2Id()).isEqualTo(2L);
        }
    }

    // ---- Tournament flow -------------------------------------------------------------------------------------

    @Nested
    @ExtendWith(MockitoExtension.class)
    @MockitoSettings(strictness = Strictness.LENIENT)
    class TournamentFlow {

        @Mock
        DeviceTournamentRepository tournamentRepository;
        @Mock
        DeviceTournamentPlayerRepository playerRepository;
        @Mock
        DeviceTournamentMatchRepository matchRepository;
        @Mock
        DeviceCustomerRepository customerRepository;
        @Mock
        DeviceCustomerTxnRepository txnRepository;
        @Mock
        DeviceSaleAccounting saleAccounting;
        @Mock
        BranchService branchService;
        @Mock
        SecurityUtils securityUtils;

        private final List<DeviceTournamentPlayer> players = new ArrayList<>();
        private final List<DeviceTournamentMatch> matches = new ArrayList<>();
        private final AtomicLong ids = new AtomicLong(100);
        private DeviceTournament tournament;
        private DeviceTournamentService service;

        @BeforeEach
        void setUp() {
            AppTime.setClock(Clock.fixed(NOW.atZone(AppTime.ZONE).toInstant(), AppTime.ZONE));
            service = new DeviceTournamentService(tournamentRepository, playerRepository, matchRepository,
                    customerRepository, txnRepository, saleAccounting, new ConsoleAccess(branchService, securityUtils));
            when(securityUtils.getCurrentMerchantId()).thenReturn(MERCHANT);
            when(securityUtils.findCurrentStaffId()).thenReturn(Optional.empty());
            tournament = DeviceTournament.builder().id(5L).merchantId(MERCHANT).branchId(BRANCH).name("FC Kupası")
                    .startsAt(NOW).entryFee(tl("50.00")).maxPlayers(8).status(DeviceTournamentStatus.REGISTRATION).build();
            when(tournamentRepository.lockOwned(5L, MERCHANT)).thenReturn(Optional.of(tournament));
            when(tournamentRepository.findByIdAndMerchantIdAndDeletedFalse(5L, MERCHANT)).thenReturn(Optional.of(tournament));
            when(playerRepository.save(any(DeviceTournamentPlayer.class))).thenAnswer(inv -> {
                DeviceTournamentPlayer p = inv.getArgument(0);
                if (p.getId() == null) {
                    p.setId(ids.incrementAndGet());
                    players.add(p);
                }
                return p;
            });
            when(playerRepository.findByTournamentIdAndDeletedFalseOrderByIdAsc(5L))
                    .thenAnswer(inv -> players.stream().filter(p -> !p.isDeleted()).toList());
            when(playerRepository.findByIdAndTournamentIdAndDeletedFalse(anyLong(), eq(5L)))
                    .thenAnswer(inv -> players.stream().filter(p -> p.getId().equals(inv.getArgument(0)) && !p.isDeleted()).findFirst());
            when(matchRepository.saveAll(any())).thenAnswer(inv -> {
                for (DeviceTournamentMatch m : (Iterable<DeviceTournamentMatch>) inv.getArgument(0)) {
                    if (m.getId() == null) {
                        m.setId(ids.incrementAndGet());
                        matches.add(m);
                    }
                }
                return inv.getArgument(0);
            });
            when(matchRepository.save(any(DeviceTournamentMatch.class))).thenAnswer(inv -> inv.getArgument(0));
            when(matchRepository.findByTournamentIdAndDeletedFalseOrderByRoundAscPositionAsc(5L)).thenAnswer(inv -> matches);
            when(matchRepository.findByIdAndTournamentIdAndDeletedFalse(anyLong(), eq(5L)))
                    .thenAnswer(inv -> matches.stream().filter(m -> m.getId().equals(inv.getArgument(0))).findFirst());
            when(matchRepository.findByTournamentIdAndRoundAndPositionAndDeletedFalse(eq(5L), anyInt(), anyInt()))
                    .thenAnswer(inv -> matches.stream()
                            .filter(m -> m.getRound() == (int) inv.getArgument(1) && m.getPosition() == (int) inv.getArgument(2))
                            .findFirst());
        }

        @AfterEach
        void reset() {
            AppTime.resetClock();
        }

        @Test
        void threePlayers_byeAdvances_finalCrownsTheWinner_andPaidFeesAreBookedAtStart() {
            service.addPlayer(5L, new ConsoleDtos.TournamentPlayerRequest(null, "Ali", null, DevicePaymentMethod.CASH));
            service.addPlayer(5L, new ConsoleDtos.TournamentPlayerRequest(null, "Can", null, DevicePaymentMethod.CARD));
            service.addPlayer(5L, new ConsoleDtos.TournamentPlayerRequest(null, "Efe", null, null));

            ConsoleDtos.TournamentResponse started = service.start(5L);

            assertThat(started.status()).isEqualTo(DeviceTournamentStatus.RUNNING);
            assertThat(started.rounds()).isEqualTo(2);
            assertThat(started.paidCount()).isEqualTo(2);
            verify(saleAccounting).book(eq(MERCHANT), eq("TOURNAMENT:5"), anyString(), eq(tl("100.00")), any(), anyString());

            ConsoleDtos.TournamentMatchResponse semi = started.matches().stream().filter(ConsoleDtos.TournamentMatchResponse::ready)
                    .findFirst().orElseThrow();
            ConsoleDtos.TournamentResponse afterSemi = service.report(5L, semi.id(),
                    new ConsoleDtos.MatchResultRequest(semi.player1Id(), "3-1"));
            ConsoleDtos.TournamentMatchResponse fin = afterSemi.matches().stream().filter(m -> m.round() == 2).findFirst().orElseThrow();
            assertThat(fin.ready()).isTrue();

            ConsoleDtos.TournamentResponse done = service.report(5L, fin.id(), new ConsoleDtos.MatchResultRequest(fin.player2Id(), null));

            assertThat(done.status()).isEqualTo(DeviceTournamentStatus.FINISHED);
            assertThat(done.winnerPlayerId()).isEqualTo(fin.player2Id());
            assertThat(done.winnerName()).isNotBlank();
        }

        @Test
        void resultCannotChange_onceTheNextMatchIsPlayed() {
            for (String name : List.of("A", "B", "C", "D")) {
                service.addPlayer(5L, new ConsoleDtos.TournamentPlayerRequest(null, name, null, null));
            }
            ConsoleDtos.TournamentResponse started = service.start(5L);
            List<ConsoleDtos.TournamentMatchResponse> semis = started.matches().stream().filter(m -> m.round() == 1).toList();
            service.report(5L, semis.get(0).id(), new ConsoleDtos.MatchResultRequest(semis.get(0).player1Id(), null));
            ConsoleDtos.TournamentResponse ready = service.report(5L, semis.get(1).id(),
                    new ConsoleDtos.MatchResultRequest(semis.get(1).player1Id(), null));
            ConsoleDtos.TournamentMatchResponse fin = ready.matches().stream().filter(m -> m.round() == 2).findFirst().orElseThrow();
            service.report(5L, fin.id(), new ConsoleDtos.MatchResultRequest(fin.player1Id(), null));

            assertThatThrownBy(() -> service.report(5L, semis.get(0).id(),
                    new ConsoleDtos.MatchResultRequest(semis.get(0).player2Id(), null)))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        void full_refusesMorePlayers_andStartNeedsTwo() {
            tournament.setMaxPlayers(2);
            service.addPlayer(5L, new ConsoleDtos.TournamentPlayerRequest(null, "A", null, null));
            assertThatThrownBy(() -> service.start(5L)).isInstanceOf(ResponseStatusException.class);
            service.addPlayer(5L, new ConsoleDtos.TournamentPlayerRequest(null, "B", null, null));
            assertThatThrownBy(() -> service.addPlayer(5L, new ConsoleDtos.TournamentPlayerRequest(null, "C", null, null)))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void unpaidStart_booksNothing_andLatePaymentIsBookedOnItsOwn() {
            service.addPlayer(5L, new ConsoleDtos.TournamentPlayerRequest(null, "A", null, null));
            service.addPlayer(5L, new ConsoleDtos.TournamentPlayerRequest(null, "B", null, null));
            service.start(5L);

            Long late = players.get(0).getId();
            service.pay(5L, late, new ConsoleDtos.TournamentPayRequest(DevicePaymentMethod.CASH));

            verify(saleAccounting).book(eq(MERCHANT), eq("TPLAYER:" + late), anyString(), eq(tl("50.00")), any(), anyString());
        }
    }

    // ---- Shift cash ------------------------------------------------------------------------------------------

    @Nested
    @ExtendWith(MockitoExtension.class)
    @MockitoSettings(strictness = Strictness.LENIENT)
    class Shift {

        @Mock
        DeviceShiftRepository shiftRepository;
        @Mock
        ConsoleSessionRepository sessionRepository;
        @Mock
        DeviceCustomerTxnRepository txnRepository;
        @Mock
        DeviceMembershipRepository membershipRepository;
        @Mock
        DeviceTournamentPlayerRepository playerRepository;
        @Mock
        DeviceTournamentRepository tournamentRepository;
        @Mock
        MerchantStaffRepository staffRepository;
        @Mock
        BranchService branchService;
        @Mock
        SecurityUtils securityUtils;

        private DeviceShiftService service;
        private final DeviceShift shift = DeviceShift.builder().id(3L).merchantId(MERCHANT).branchId(BRANCH).staffId(21L)
                .openedAt(NOW.minusHours(8)).openingCash(tl("200.00")).build();

        @BeforeEach
        void setUp() {
            AppTime.setClock(Clock.fixed(NOW.atZone(AppTime.ZONE).toInstant(), AppTime.ZONE));
            service = new DeviceShiftService(shiftRepository, sessionRepository, txnRepository, membershipRepository,
                    playerRepository, tournamentRepository, staffRepository, new ConsoleAccess(branchService, securityUtils));
            when(securityUtils.getCurrentMerchantId()).thenReturn(MERCHANT);
            when(securityUtils.findCurrentStaffId()).thenReturn(Optional.of(21L));
            when(securityUtils.getCurrentWaiterBranchId()).thenReturn(BRANCH);
            when(shiftRepository.findByIdAndMerchantIdAndDeletedFalse(3L, MERCHANT)).thenReturn(Optional.of(shift));
            when(shiftRepository.save(any(DeviceShift.class))).thenAnswer(inv -> inv.getArgument(0));
        }

        @AfterEach
        void reset() {
            AppTime.resetClock();
        }

        private ConsoleSession closed(Long staffId, String amount, DevicePaymentMethod method) {
            return ConsoleSession.builder().id(ThreadLocalIds.next()).branchId(BRANCH).merchantId(MERCHANT).consoleId(7L)
                    .startedAt(NOW.minusHours(2)).endedAt(NOW.minusHours(1)).closedByStaffId(staffId)
                    .amount(tl(amount)).paymentMethod(method).hourlyRate(tl("100")).build();
        }

        @Test
        void close_countsOnlyTheirCash_andShowsTheDifference() {
            when(sessionRepository.findClosedAtBranch(eq(BRANCH), any(), any())).thenReturn(List.of(
                    closed(21L, "150.00", DevicePaymentMethod.CASH),
                    closed(21L, "90.00", DevicePaymentMethod.CARD),
                    closed(22L, "500.00", DevicePaymentMethod.CASH)));
            when(txnRepository.findAtBranch(eq(BRANCH), any(), any())).thenReturn(List.of(
                    DeviceCustomerTxn.builder().txnType(com.ael.algoryqrservice.qrservice.console.domain.DeviceCustomerTxnType.TOPUP)
                            .staffId(21L).amount(tl("100.00")).paymentMethod(DevicePaymentMethod.CASH).build()));

            ConsoleDtos.ShiftResponse closed = service.close(3L, new ConsoleDtos.CloseShiftRequest(tl("440.00"), null));

            // 200 opening + 150 session cash + 100 top-up cash = 450 expected; counted 440.
            assertThat(closed.sessions()).isEqualTo(2);
            assertThat(closed.cashIn()).isEqualByComparingTo("250.00");
            assertThat(closed.expectedCash()).isEqualByComparingTo("450.00");
            assertThat(closed.difference()).isEqualByComparingTo("-10.00");
            assertThat(shift.getClosedAt()).isEqualTo(NOW);
        }

        @Test
        void anotherStaffMembersShift_isNotFound() {
            when(securityUtils.findCurrentStaffId()).thenReturn(Optional.of(99L));
            assertThatThrownBy(() -> service.close(3L, new ConsoleDtos.CloseShiftRequest(tl("0"), null)))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ---- Public device page ----------------------------------------------------------------------------------

    @Nested
    @ExtendWith(MockitoExtension.class)
    @MockitoSettings(strictness = Strictness.LENIENT)
    class PublicPage {

        @Mock
        GameConsoleRepository consoleRepository;
        @Mock
        ConsoleSessionRepository sessionRepository;
        @Mock
        BranchRepository branchRepository;
        @Mock
        ConsoleCatalogService catalogService;
        @Mock
        ConsoleSessionService sessionService;
        @Mock
        ProductUsageGateway productUsageGateway;

        private DevicePublicService service;
        private final GameConsole console = GameConsole.builder().id(7L).merchantId(MERCHANT).branchId(BRANCH).name("PS5 - 1")
                .deviceType(DeviceType.PLAYSTATION).publicToken("abcdefghijklmnopqrstuvwx").build();

        @BeforeEach
        void setUp() {
            service = new DevicePublicService(consoleRepository, sessionRepository, branchRepository, catalogService,
                    sessionService, productUsageGateway);
            when(consoleRepository.findByPublicTokenAndDeletedFalse("abcdefghijklmnopqrstuvwx")).thenReturn(Optional.of(console));
            when(catalogService.rateContext(BRANCH, MERCHANT)).thenReturn(new ConsoleCatalogService.RateContext(
                    null, Map.of(), DeviceRentalPricing.Rules.NONE));
            when(catalogService.gamesByConsole(any())).thenReturn(Map.of());
            Branch branch = new Branch();
            branch.setName("Kadıköy");
            when(branchRepository.findById(BRANCH)).thenReturn(Optional.of(branch));
        }

        @Test
        void freeDevice_showsNoSession() {
            ConsoleDtos.PublicDeviceResponse page = service.device("abcdefghijklmnopqrstuvwx");
            assertThat(page.status()).isEqualTo("FREE");
            assertThat(page.session()).isNull();
            assertThat(page.branchName()).isEqualTo("Kadıköy");
        }

        @Test
        void unknownOrShortToken_isNotFound() {
            assertThatThrownBy(() -> service.device("nope")).isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(() -> service.device("zzzzzzzzzzzzzzzzzzzzzzzz")).isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void merchantWithoutTheAddOn_isNotFound() {
            doThrow(new IllegalStateException("FEATURE_NOT_IN_PLAN"))
                    .when(productUsageGateway).allow(MERCHANT, "CONSOLE_RENTAL");
            assertThatThrownBy(() -> service.device("abcdefghijklmnopqrstuvwx"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void extensionRequest_withoutASession_isRefused() {
            when(sessionRepository.findFirstByConsoleIdAndEndedAtIsNullAndDeletedFalse(7L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.requestExtension("abcdefghijklmnopqrstuvwx", new ConsoleDtos.PublicExtensionRequest(30)))
                    .isInstanceOf(ResponseStatusException.class);
            verify(sessionRepository, never()).save(any());
        }
    }

    /** Distinct ids for throwaway rows. */
    private static final class ThreadLocalIds {
        private static final AtomicLong NEXT = new AtomicLong(1000);

        static Long next() {
            return NEXT.incrementAndGet();
        }
    }
}
