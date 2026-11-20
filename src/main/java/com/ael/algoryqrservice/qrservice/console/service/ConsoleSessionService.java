package com.ael.algoryqrservice.qrservice.console.service;

import com.ael.algoryqrservice.qrservice.branch.service.BranchService;
import com.ael.algoryqrservice.qrservice.console.domain.ConsoleRentalPricing;
import com.ael.algoryqrservice.qrservice.console.dto.ConsoleDtos;
import com.ael.algoryqrservice.qrservice.model.ConsoleSession;
import com.ael.algoryqrservice.qrservice.model.GameConsole;
import com.ael.algoryqrservice.qrservice.repository.ConsoleSessionRepository;
import com.ael.algoryqrservice.qrservice.repository.GameConsoleRepository;
import com.ael.algoryqrservice.qrservice.util.AppTime;
import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Starting, finishing and listing console rentals. */
@Service
@RequiredArgsConstructor
public class ConsoleSessionService {

    private final ConsoleSessionRepository sessionRepository;
    private final GameConsoleRepository consoleRepository;
    private final ConsoleCatalogService catalogService;
    private final BranchService branchService;
    private final SecurityUtils securityUtils;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ConsoleDtos.SessionResponse start(Long consoleId, ConsoleDtos.StartSessionRequest request) {
        Long merchantId = securityUtils.getCurrentMerchantId();
        GameConsole console = consoleRepository.lockOwned(consoleId, merchantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Konsol bulunamadı"));
        if (sessionRepository.existsByConsoleIdAndEndedAtIsNullAndDeletedFalse(console.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu konsolda zaten açık bir oturum var");
        }
        BigDecimal rate = catalogService.effectiveRate(console);
        if (rate == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Saatlik ücret tanımlı değil; konsola veya şubeye ücret girin"
            );
        }
        ConsoleSession saved = sessionRepository.save(ConsoleSession.builder()
                .merchantId(console.getMerchantId())
                .branchId(console.getBranchId())
                .consoleId(console.getId())
                .customerName(blankToNull(request == null ? null : request.customerName()))
                .note(blankToNull(request == null ? null : request.note()))
                .hourlyRate(rate)
                .startedAt(AppTime.nowLocal())
                .deleted(false)
                .build());
        return toResponse(saved, console.getName(), AppTime.nowLocal());
    }

    @Transactional
    public ConsoleDtos.SessionResponse finish(Long sessionId) {
        ConsoleSession session = sessionRepository.lockOwned(sessionId, securityUtils.getCurrentMerchantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Oturum bulunamadı"));
        if (!session.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Oturum zaten bitmiş");
        }
        LocalDateTime endedAt = AppTime.nowLocal();
        int minutes = ConsoleRentalPricing.billableMinutes(session.getStartedAt(), endedAt);
        session.setEndedAt(endedAt);
        session.setDurationMinutes(minutes);
        session.setAmount(ConsoleRentalPricing.amount(session.getHourlyRate(), minutes));
        ConsoleSession saved = sessionRepository.save(session);
        eventPublisher.publishEvent(new ConsoleSessionFinishedEvent(saved.getId()));
        return toResponse(saved, consoleNames(List.of(saved.getConsoleId())).get(saved.getConsoleId()), endedAt);
    }

    @Transactional(readOnly = true)
    public List<ConsoleDtos.SessionResponse> listActive(Long branchId) {
        var branch = branchService.requireOwned(branchId);
        List<ConsoleSession> sessions = sessionRepository
                .findByBranchIdAndMerchantIdAndEndedAtIsNullAndDeletedFalseOrderByStartedAtAscIdAsc(
                        branch.getId(),
                        branch.getMerchantId()
                );
        Map<Long, String> names = consoleNames(sessions.stream().map(ConsoleSession::getConsoleId).toList());
        LocalDateTime now = AppTime.nowLocal();
        return sessions.stream().map(s -> toResponse(s, names.get(s.getConsoleId()), now)).toList();
    }

    @Transactional(readOnly = true)
    public ConsoleDtos.SessionPageResponse listHistory(Long branchId, int page, int size) {
        var branch = branchService.requireOwned(branchId);
        Page<ConsoleSession> result = sessionRepository
                .findByBranchIdAndMerchantIdAndEndedAtIsNotNullAndDeletedFalseOrderByEndedAtDescIdDesc(
                        branch.getId(),
                        branch.getMerchantId(),
                        PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))
                );
        Map<Long, String> names = consoleNames(
                result.getContent().stream().map(ConsoleSession::getConsoleId).toList()
        );
        LocalDateTime now = AppTime.nowLocal();
        return new ConsoleDtos.SessionPageResponse(
                result.getContent().stream().map(s -> toResponse(s, names.get(s.getConsoleId()), now)).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    private Map<Long, String> consoleNames(Collection<Long> consoleIds) {
        if (consoleIds.isEmpty()) {
            return Map.of();
        }
        return consoleRepository.findByIdIn(consoleIds.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(GameConsole::getId, GameConsole::getName, (a, b) -> a));
    }

    private ConsoleDtos.SessionResponse toResponse(ConsoleSession session, String consoleName, LocalDateTime now) {
        int minutes;
        BigDecimal amount;
        if (session.isActive()) {
            minutes = ConsoleRentalPricing.billableMinutes(session.getStartedAt(), now);
            amount = ConsoleRentalPricing.amount(session.getHourlyRate(), minutes);
        } else {
            minutes = session.getDurationMinutes() == null ? 0 : session.getDurationMinutes();
            amount = session.getAmount();
        }
        return new ConsoleDtos.SessionResponse(
                session.getId(),
                session.getBranchId(),
                session.getConsoleId(),
                consoleName,
                session.getCustomerName(),
                session.getNote(),
                session.getHourlyRate(),
                session.getStartedAt(),
                session.getEndedAt(),
                minutes,
                amount,
                session.isActive()
        );
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
