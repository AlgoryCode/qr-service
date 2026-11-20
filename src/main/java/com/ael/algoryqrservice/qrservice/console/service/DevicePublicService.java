package com.ael.algoryqrservice.qrservice.console.service;

import com.ael.algoryqrservice.qrservice.console.dto.ConsoleDtos;
import com.ael.algoryqrservice.qrservice.model.Branch;
import com.ael.algoryqrservice.qrservice.model.ConsoleSession;
import com.ael.algoryqrservice.qrservice.model.GameConsole;
import com.ael.algoryqrservice.qrservice.repository.BranchRepository;
import com.ael.algoryqrservice.qrservice.repository.ConsoleSessionRepository;
import com.ael.algoryqrservice.qrservice.repository.GameConsoleRepository;
import com.ael.algoryqrservice.qrservice.security.FeatureKey;
import com.ael.algoryqrservice.qrservice.security.ProductUsageGateway;
import com.ael.algoryqrservice.qrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The page a customer opens by scanning the QR on a device: the running session's time and amount, and a way to
 * ask the staff for more time. It shows nothing about who is playing. A merchant without the add-on, or a removed
 * device, answers 404.
 */
@Service
@RequiredArgsConstructor
public class DevicePublicService {

    private final GameConsoleRepository consoleRepository;
    private final ConsoleSessionRepository sessionRepository;
    private final BranchRepository branchRepository;
    private final ConsoleCatalogService catalogService;
    private final ConsoleSessionService sessionService;
    private final ProductUsageGateway productUsageGateway;

    @Transactional(readOnly = true)
    public ConsoleDtos.PublicDeviceResponse device(String token) {
        GameConsole console = require(token);
        ConsoleSession session = sessionRepository.findFirstByConsoleIdAndEndedAtIsNullAndDeletedFalse(console.getId()).orElse(null);
        return response(console, session);
    }

    /** Asks the staff for more time; a new request replaces one still waiting. */
    @Transactional
    public ConsoleDtos.PublicDeviceResponse requestExtension(String token, ConsoleDtos.PublicExtensionRequest request) {
        GameConsole console = require(token);
        ConsoleSession session = sessionRepository.findFirstByConsoleIdAndEndedAtIsNullAndDeletedFalse(console.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Bu cihazda açık oturum yok"));
        session = sessionRepository.lockOwned(session.getId(), session.getMerchantId())
                .filter(ConsoleSession::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Bu cihazda açık oturum yok"));
        session.setExtensionRequestMinutes(request.minutes());
        session.setExtensionRequestedAt(AppTime.nowLocal());
        return response(console, sessionRepository.save(session));
    }

    private GameConsole require(String token) {
        if (token == null || token.length() < 12 || token.length() > 40) {
            throw notFound();
        }
        GameConsole console = consoleRepository.findByPublicTokenAndDeletedFalse(token).orElseThrow(DevicePublicService::notFound);
        try {
            productUsageGateway.allow(console.getMerchantId(), FeatureKey.CONSOLE_RENTAL.name());
        } catch (RuntimeException notAllowed) {
            throw notFound();
        }
        return console;
    }

    private ConsoleDtos.PublicDeviceResponse response(GameConsole console, ConsoleSession session) {
        String branchName = branchRepository.findById(console.getBranchId()).map(Branch::getName).orElse(null);
        ConsoleCatalogService.RateContext rates = catalogService.rateContext(console.getBranchId(), console.getMerchantId());
        List<String> games = catalogService.gamesByConsole(List.of(console.getId()))
                .getOrDefault(console.getId(), List.of()).stream()
                .map(ConsoleDtos.GameRef::name)
                .toList();
        LocalDateTime now = AppTime.nowLocal();
        ConsoleDtos.PublicSessionResponse publicSession = null;
        String status = console.isMaintenance() ? "MAINTENANCE" : "FREE";
        if (session != null && session.isActive()) {
            ConsoleDtos.SessionResponse s = sessionService.responses(List.of(session), rates).get(0);
            status = s.paused() ? "PAUSED" : "BUSY";
            Long remaining = s.plannedMinutes() == null ? null : s.plannedMinutes() * 60L - s.playedSeconds();
            publicSession = new ConsoleDtos.PublicSessionResponse(
                    s.startedAt(),
                    s.playedSeconds(),
                    s.plannedMinutes(),
                    remaining,
                    s.paused(),
                    s.packageName(),
                    s.gameName(),
                    s.hourlyRate(),
                    s.timeAmount(),
                    s.itemsAmount(),
                    s.grossAmount(),
                    s.items().stream().map(i -> new ConsoleDtos.PublicItem(i.name(), i.quantity(), i.lineTotal())).toList(),
                    s.extensionRequestMinutes(),
                    s.extensionRequestedAt()
            );
        }
        return new ConsoleDtos.PublicDeviceResponse(
                console.getName(),
                console.getDeviceType(),
                branchName,
                status,
                rates.rate(console, ConsoleSessionService.DEFAULT_CONTROLLERS),
                games,
                publicSession,
                now
        );
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Cihaz bulunamadı");
    }
}
