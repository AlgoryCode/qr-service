package com.ael.algoryqrservice.qrservice.console.service;

import com.ael.algoryqrservice.qrservice.console.domain.DeviceCustomerTxnType;
import com.ael.algoryqrservice.qrservice.console.domain.DevicePaymentMethod;
import com.ael.algoryqrservice.qrservice.console.domain.DeviceTournamentStatus;
import com.ael.algoryqrservice.qrservice.console.dto.ConsoleDtos;
import com.ael.algoryqrservice.qrservice.model.Branch;
import com.ael.algoryqrservice.qrservice.model.DeviceCustomer;
import com.ael.algoryqrservice.qrservice.model.DeviceCustomerTxn;
import com.ael.algoryqrservice.qrservice.model.DeviceTournament;
import com.ael.algoryqrservice.qrservice.model.DeviceTournamentMatch;
import com.ael.algoryqrservice.qrservice.model.DeviceTournamentPlayer;
import com.ael.algoryqrservice.qrservice.repository.DeviceCustomerRepository;
import com.ael.algoryqrservice.qrservice.repository.DeviceCustomerTxnRepository;
import com.ael.algoryqrservice.qrservice.repository.DeviceTournamentMatchRepository;
import com.ael.algoryqrservice.qrservice.repository.DeviceTournamentPlayerRepository;
import com.ael.algoryqrservice.qrservice.repository.DeviceTournamentRepository;
import com.ael.algoryqrservice.qrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Single-elimination tournaments. Players sign up (paying the entry fee then or later); starting draws the bracket
 * at random, giving byes in the first round when the count is not a power of two. Each result moves the winner on;
 * the final's winner wins the tournament.
 *
 * <p>Fees paid before the start are booked as income in one entry when the tournament starts (so a player removed
 * or a tournament cancelled before then books nothing); a fee paid after the start is booked on its own.
 */
@Service
@RequiredArgsConstructor
public class DeviceTournamentService {

    private final DeviceTournamentRepository tournamentRepository;
    private final DeviceTournamentPlayerRepository playerRepository;
    private final DeviceTournamentMatchRepository matchRepository;
    private final DeviceCustomerRepository customerRepository;
    private final DeviceCustomerTxnRepository txnRepository;
    private final DeviceSaleAccounting saleAccounting;
    private final ConsoleAccess access;

    @Transactional(readOnly = true)
    public List<ConsoleDtos.TournamentResponse> list(Long branchId) {
        Branch branch = access.requireBranch(branchId);
        List<DeviceTournament> tournaments = tournamentRepository
                .findByBranchIdAndMerchantIdAndDeletedFalseOrderByStartsAtDescIdDesc(branch.getId(), branch.getMerchantId());
        if (tournaments.isEmpty()) {
            return List.of();
        }
        Map<Long, List<DeviceTournamentPlayer>> players = playerRepository
                .findByTournamentIdInAndDeletedFalse(tournaments.stream().map(DeviceTournament::getId).toList()).stream()
                .collect(Collectors.groupingBy(DeviceTournamentPlayer::getTournamentId));
        return tournaments.stream()
                .map(t -> response(t, players.getOrDefault(t.getId(), List.of()), null))
                .toList();
    }

    @Transactional(readOnly = true)
    public ConsoleDtos.TournamentResponse get(Long tournamentId) {
        return full(require(tournamentId));
    }

    @Transactional
    public ConsoleDtos.TournamentResponse create(Long branchId, ConsoleDtos.TournamentRequest request) {
        Branch branch = access.requireBranch(branchId);
        DeviceTournament tournament = DeviceTournament.builder()
                .merchantId(branch.getMerchantId())
                .branchId(branch.getId())
                .status(DeviceTournamentStatus.REGISTRATION)
                .deleted(false)
                .build();
        apply(tournament, request);
        return full(tournamentRepository.save(tournament));
    }

    @Transactional
    public ConsoleDtos.TournamentResponse update(Long tournamentId, ConsoleDtos.TournamentRequest request) {
        DeviceTournament tournament = lockInRegistration(tournamentId);
        int players = playerRepository.findByTournamentIdAndDeletedFalseOrderByIdAsc(tournament.getId()).size();
        if (request.maxPlayers() < players) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kayıtlı oyuncu sayısından az kontenjan girilemez");
        }
        apply(tournament, request);
        return full(tournamentRepository.save(tournament));
    }

    @Transactional
    public ConsoleDtos.TournamentResponse cancel(Long tournamentId) {
        DeviceTournament tournament = lockInRegistration(tournamentId);
        tournament.setStatus(DeviceTournamentStatus.CANCELLED);
        return full(tournamentRepository.save(tournament));
    }

    // ---- Players -----------------------------------------------------------------------------------------------

    @Transactional
    public ConsoleDtos.TournamentResponse addPlayer(Long tournamentId, ConsoleDtos.TournamentPlayerRequest request) {
        DeviceTournament tournament = lockInRegistration(tournamentId);
        List<DeviceTournamentPlayer> players = playerRepository.findByTournamentIdAndDeletedFalseOrderByIdAsc(tournament.getId());
        if (players.size() >= tournament.getMaxPlayers()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Kontenjan dolu");
        }
        DeviceCustomer customer = null;
        if (request.customerId() != null) {
            customer = customerRepository.findByIdAndMerchantIdAndDeletedFalse(request.customerId(), tournament.getMerchantId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Müşteri bulunamadı"));
            Long customerId = customer.getId();
            if (players.stream().anyMatch(p -> Objects.equals(p.getCustomerId(), customerId))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu müşteri zaten kayıtlı");
            }
        }
        String name = DeviceMappers.blankToNull(request.name());
        if (name == null && customer != null) {
            name = customer.getName();
        }
        if (name == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Oyuncu adı girin veya müşteri kartı seçin");
        }
        DeviceTournamentPlayer player = playerRepository.save(DeviceTournamentPlayer.builder()
                .tournamentId(tournament.getId())
                .customerId(customer == null ? null : customer.getId())
                .name(name)
                .phone(DeviceMappers.normalizePhone(request.phone() != null ? request.phone() : customer == null ? null : customer.getPhone()))
                .paid(false)
                .deleted(false)
                .build());
        if (request.paymentMethod() != null) {
            markPaid(tournament, player, request.paymentMethod());
        }
        return full(tournament);
    }

    /** Only before the start; a paid fee is handed back in cash, so it leaves the shift too. */
    @Transactional
    public ConsoleDtos.TournamentResponse removePlayer(Long tournamentId, Long playerId) {
        DeviceTournament tournament = lockInRegistration(tournamentId);
        DeviceTournamentPlayer player = requirePlayer(tournament, playerId);
        if (player.isPaid() && player.getPaymentMethod() == DevicePaymentMethod.BALANCE && player.getCustomerId() != null) {
            customerRepository.lockOwned(player.getCustomerId(), tournament.getMerchantId()).ifPresent(customer -> {
                customer.setBalance(customer.getBalance().add(tournament.getEntryFee()));
                customerRepository.save(customer);
                txn(tournament, customer, tournament.getEntryFee(), "Turnuva iadesi: " + tournament.getName());
            });
        }
        player.setDeleted(true);
        playerRepository.save(player);
        return full(tournament);
    }

    @Transactional
    public ConsoleDtos.TournamentResponse pay(Long tournamentId, Long playerId, ConsoleDtos.TournamentPayRequest request) {
        DeviceTournament tournament = lockOwned(tournamentId);
        if (tournament.getStatus() == DeviceTournamentStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Turnuva iptal edilmiş");
        }
        DeviceTournamentPlayer player = requirePlayer(tournament, playerId);
        if (player.isPaid()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ücret zaten alınmış");
        }
        markPaid(tournament, player, request == null || request.paymentMethod() == null ? DevicePaymentMethod.CASH : request.paymentMethod());
        if (tournament.getStatus() != DeviceTournamentStatus.REGISTRATION) {
            saleAccounting.book(tournament.getMerchantId(), "TPLAYER:" + player.getId(),
                    "Turnuva katılımı - " + tournament.getName(), tournament.getEntryFee(), player.getPaidAt(),
                    player.getName() + " · Ödeme: " + player.getPaymentMethod().label());
        }
        return full(tournament);
    }

    private void markPaid(DeviceTournament tournament, DeviceTournamentPlayer player, DevicePaymentMethod method) {
        BigDecimal fee = tournament.getEntryFee();
        if (method == DevicePaymentMethod.BALANCE && fee.signum() > 0) {
            if (player.getCustomerId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bakiyeden ödeme için müşteri kartı gerekir");
            }
            DeviceCustomer customer = customerRepository.lockOwned(player.getCustomerId(), tournament.getMerchantId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Müşteri bulunamadı"));
            if (customer.getBalance().compareTo(fee) < 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Müşterinin bakiyesi yetersiz");
            }
            customer.setBalance(customer.getBalance().subtract(fee));
            customerRepository.save(customer);
            txn(tournament, customer, fee.negate(), "Turnuva: " + tournament.getName());
        }
        player.setPaid(true);
        player.setPaymentMethod(method);
        player.setPaidAt(AppTime.nowLocal());
        player.setStaffId(access.staffId());
        playerRepository.save(player);
    }

    private void txn(DeviceTournament tournament, DeviceCustomer customer, BigDecimal amount, String note) {
        txnRepository.save(DeviceCustomerTxn.builder()
                .merchantId(tournament.getMerchantId())
                .customerId(customer.getId())
                .branchId(tournament.getBranchId())
                .staffId(access.staffId())
                .txnType(amount.signum() < 0 ? DeviceCustomerTxnType.SPEND : DeviceCustomerTxnType.ADJUST)
                .amount(amount)
                .paymentMethod(DevicePaymentMethod.BALANCE)
                .note(note.length() > 255 ? note.substring(0, 255) : note)
                .deleted(false)
                .build());
    }

    // ---- Bracket -----------------------------------------------------------------------------------------------

    @Transactional
    public ConsoleDtos.TournamentResponse start(Long tournamentId) {
        DeviceTournament tournament = lockInRegistration(tournamentId);
        List<DeviceTournamentPlayer> players = new ArrayList<>(
                playerRepository.findByTournamentIdAndDeletedFalseOrderByIdAsc(tournament.getId()));
        if (players.size() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Başlatmak için en az 2 oyuncu gerekir");
        }
        Collections.shuffle(players);
        List<DeviceTournamentMatch> matches = draw(tournament.getId(), players.stream().map(DeviceTournamentPlayer::getId).toList());
        matchRepository.saveAll(matches);
        tournament.setStatus(DeviceTournamentStatus.RUNNING);
        int rounds = rounds(players.size());
        // Byes: a first-round match with one player sends that player straight on.
        for (DeviceTournamentMatch match : matches) {
            if (match.getRound() == 1 && match.getPlayer2Id() == null && match.getPlayer1Id() != null) {
                match.setWinnerId(match.getPlayer1Id());
                match.setScore("Bay");
                advance(tournament, match, rounds);
            }
        }
        matchRepository.saveAll(matches);
        tournamentRepository.save(tournament);

        BigDecimal collected = tournament.getEntryFee().multiply(BigDecimal.valueOf(players.stream().filter(DeviceTournamentPlayer::isPaid).count()));
        saleAccounting.book(tournament.getMerchantId(), "TOURNAMENT:" + tournament.getId(),
                "Turnuva katılımı - " + tournament.getName(), collected.setScale(2, RoundingMode.HALF_UP), AppTime.nowLocal(),
                players.stream().filter(DeviceTournamentPlayer::isPaid).count() + " oyuncu × "
                        + tournament.getEntryFee().stripTrailingZeros().toPlainString() + " TL");
        return full(tournament);
    }

    /** Records (or corrects, while the next match is unplayed) who won a match. */
    @Transactional
    public ConsoleDtos.TournamentResponse report(Long tournamentId, Long matchId, ConsoleDtos.MatchResultRequest request) {
        DeviceTournament tournament = lockOwned(tournamentId);
        if (tournament.getStatus() != DeviceTournamentStatus.RUNNING && tournament.getStatus() != DeviceTournamentStatus.FINISHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Turnuva başlamamış");
        }
        DeviceTournamentMatch match = matchRepository.findByIdAndTournamentIdAndDeletedFalse(matchId, tournament.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Maç bulunamadı"));
        if (match.getPlayer1Id() == null || match.getPlayer2Id() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Maçın iki oyuncusu da belli değil");
        }
        if (!Objects.equals(request.winnerPlayerId(), match.getPlayer1Id()) && !Objects.equals(request.winnerPlayerId(), match.getPlayer2Id())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kazanan bu maçın oyuncularından biri olmalı");
        }
        int rounds = maxRound(tournament.getId());
        if (match.getWinnerId() != null && match.getRound() < rounds) {
            DeviceTournamentMatch next = next(tournament.getId(), match);
            if (next != null && next.getWinnerId() != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Sonraki maç oynanmış; sonuç değiştirilemez");
            }
        }
        match.setWinnerId(request.winnerPlayerId());
        match.setScore(DeviceMappers.blankToNull(request.score()));
        matchRepository.save(match);
        advance(tournament, match, rounds);
        tournamentRepository.save(tournament);
        return full(tournament);
    }

    /** First-round pairs; with a count that is not a power of two the first matches get a single player (a bye). */
    static List<DeviceTournamentMatch> draw(Long tournamentId, List<Long> playerIds) {
        int rounds = rounds(playerIds.size());
        int size = 1 << rounds;
        int firstRound = size / 2;
        List<DeviceTournamentMatch> matches = new ArrayList<>();
        for (int round = 1; round <= rounds; round++) {
            int count = size >> round;
            for (int position = 0; position < count; position++) {
                DeviceTournamentMatch match = DeviceTournamentMatch.builder()
                        .tournamentId(tournamentId)
                        .round(round)
                        .position(position)
                        .deleted(false)
                        .build();
                if (round == 1) {
                    match.setPlayer1Id(playerIds.get(position));
                    // Second players fill the last matches, so the byes sit at the top of the bracket.
                    int second = firstRound + position - (size - playerIds.size());
                    if (second >= firstRound && second < playerIds.size()) {
                        match.setPlayer2Id(playerIds.get(second));
                    }
                }
                matches.add(match);
            }
        }
        return matches;
    }

    static int rounds(int players) {
        int rounds = 1;
        while ((1 << rounds) < players) {
            rounds++;
        }
        return rounds;
    }

    /** Puts the winner into the next match, or ends the tournament after the final. */
    private void advance(DeviceTournament tournament, DeviceTournamentMatch match, int rounds) {
        if (match.getRound() >= rounds) {
            tournament.setWinnerPlayerId(match.getWinnerId());
            tournament.setStatus(DeviceTournamentStatus.FINISHED);
            return;
        }
        DeviceTournamentMatch next = next(tournament.getId(), match);
        if (next == null) {
            return;
        }
        if (match.getPosition() % 2 == 0) {
            next.setPlayer1Id(match.getWinnerId());
        } else {
            next.setPlayer2Id(match.getWinnerId());
        }
        matchRepository.save(next);
    }

    private DeviceTournamentMatch next(Long tournamentId, DeviceTournamentMatch match) {
        return matchRepository.findByTournamentIdAndRoundAndPositionAndDeletedFalse(
                tournamentId, match.getRound() + 1, match.getPosition() / 2).orElse(null);
    }

    private int maxRound(Long tournamentId) {
        return matchRepository.findByTournamentIdAndDeletedFalseOrderByRoundAscPositionAsc(tournamentId).stream()
                .mapToInt(DeviceTournamentMatch::getRound)
                .max()
                .orElse(1);
    }

    // ---- Helpers -----------------------------------------------------------------------------------------------

    private static void apply(DeviceTournament tournament, ConsoleDtos.TournamentRequest request) {
        tournament.setName(request.name().trim());
        tournament.setGameName(DeviceMappers.blankToNull(request.gameName()));
        tournament.setStartsAt(request.startsAt().withSecond(0).withNano(0));
        tournament.setEntryFee(request.entryFee() == null ? BigDecimal.ZERO.setScale(2) : request.entryFee().setScale(2, RoundingMode.HALF_UP));
        tournament.setPrizeNote(DeviceMappers.blankToNull(request.prizeNote()));
        tournament.setMaxPlayers(request.maxPlayers());
    }

    private DeviceTournament require(Long tournamentId) {
        DeviceTournament tournament = tournamentRepository.findByIdAndMerchantIdAndDeletedFalse(tournamentId, access.merchantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Turnuva bulunamadı"));
        access.checkBranch(tournament.getBranchId());
        return tournament;
    }

    private DeviceTournament lockOwned(Long tournamentId) {
        DeviceTournament tournament = tournamentRepository.lockOwned(tournamentId, access.merchantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Turnuva bulunamadı"));
        access.checkBranch(tournament.getBranchId());
        return tournament;
    }

    private DeviceTournament lockInRegistration(Long tournamentId) {
        DeviceTournament tournament = lockOwned(tournamentId);
        if (tournament.getStatus() != DeviceTournamentStatus.REGISTRATION) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Turnuva başladı veya kapandı; bu işlem yapılamaz");
        }
        return tournament;
    }

    private DeviceTournamentPlayer requirePlayer(DeviceTournament tournament, Long playerId) {
        return playerRepository.findByIdAndTournamentIdAndDeletedFalse(playerId, tournament.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Oyuncu bulunamadı"));
    }

    private ConsoleDtos.TournamentResponse full(DeviceTournament tournament) {
        return response(
                tournament,
                playerRepository.findByTournamentIdAndDeletedFalseOrderByIdAsc(tournament.getId()),
                matchRepository.findByTournamentIdAndDeletedFalseOrderByRoundAscPositionAsc(tournament.getId())
        );
    }

    /** {@code matches} null leaves them out (lists). */
    static ConsoleDtos.TournamentResponse response(
            DeviceTournament t,
            List<DeviceTournamentPlayer> players,
            List<DeviceTournamentMatch> matches
    ) {
        Map<Long, DeviceTournamentPlayer> byId = new HashMap<>();
        players.forEach(p -> byId.put(p.getId(), p));
        Function<Long, String> name = id -> {
            if (id == null) {
                return null;
            }
            DeviceTournamentPlayer p = byId.get(id);
            return p == null ? "Çıkarılan oyuncu" : p.getName();
        };
        long paid = players.stream().filter(DeviceTournamentPlayer::isPaid).count();
        int rounds = matches == null || matches.isEmpty()
                ? rounds(Math.max(players.size(), 2))
                : matches.stream().mapToInt(DeviceTournamentMatch::getRound).max().orElse(1);
        return new ConsoleDtos.TournamentResponse(
                t.getId(),
                t.getBranchId(),
                t.getName(),
                t.getGameName(),
                t.getStartsAt(),
                t.getEntryFee(),
                t.getPrizeNote(),
                t.getMaxPlayers(),
                t.getStatus(),
                t.getWinnerPlayerId(),
                name.apply(t.getWinnerPlayerId()),
                players.size(),
                (int) paid,
                t.getEntryFee().multiply(BigDecimal.valueOf(paid)).setScale(2, RoundingMode.HALF_UP),
                rounds,
                players.stream().map(p -> new ConsoleDtos.TournamentPlayerResponse(
                        p.getId(), p.getCustomerId(), p.getName(), p.getPhone(), p.isPaid(), p.getPaymentMethod(), p.getPaidAt()
                )).toList(),
                matches == null ? List.of() : matches.stream().map(m -> new ConsoleDtos.TournamentMatchResponse(
                        m.getId(),
                        m.getRound(),
                        m.getPosition(),
                        m.getPlayer1Id(),
                        name.apply(m.getPlayer1Id()),
                        m.getPlayer2Id(),
                        name.apply(m.getPlayer2Id()),
                        m.getWinnerId(),
                        m.getScore(),
                        m.getPlayer1Id() != null && m.getPlayer2Id() != null && m.getWinnerId() == null
                )).toList()
        );
    }
}
