package com.ael.algoryqrservice.qrservice.console.service;

import com.ael.algoryqrservice.qrservice.console.domain.DeviceMaintenanceKind;
import com.ael.algoryqrservice.qrservice.console.dto.ConsoleDtos;
import com.ael.algoryqrservice.qrservice.model.DeviceMaintenanceLog;
import com.ael.algoryqrservice.qrservice.model.GameConsole;
import com.ael.algoryqrservice.qrservice.repository.ConsoleSessionRepository;
import com.ael.algoryqrservice.qrservice.repository.DeviceMaintenanceLogRepository;
import com.ael.algoryqrservice.qrservice.repository.GameConsoleRepository;
import com.ael.algoryqrservice.qrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Taking a device out of service (fault, repair) and back, and recording what it cost. The costs count against the
 * device in the profitability report.
 */
@Service
@RequiredArgsConstructor
public class DeviceMaintenanceService {

    private final GameConsoleRepository consoleRepository;
    private final ConsoleSessionRepository sessionRepository;
    private final DeviceMaintenanceLogRepository logRepository;
    private final ConsoleCatalogService catalogService;
    private final ConsoleAccess access;

    @Transactional
    public ConsoleDtos.ConsoleResponse start(Long consoleId, ConsoleDtos.MaintenanceStartRequest request) {
        GameConsole console = lock(consoleId);
        if (console.isMaintenance()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cihaz zaten bakımda");
        }
        if (sessionRepository.existsByConsoleIdAndEndedAtIsNullAndDeletedFalse(console.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cihazda açık oturum var; önce bitirin veya başka cihaza taşıyın");
        }
        LocalDateTime now = AppTime.nowLocal();
        String note = DeviceMappers.blankToNull(request == null ? null : request.note());
        console.setMaintenance(true);
        console.setMaintenanceNote(note);
        console.setMaintenanceSince(now);
        consoleRepository.save(console);
        logRepository.save(DeviceMaintenanceLog.builder()
                .merchantId(console.getMerchantId())
                .branchId(console.getBranchId())
                .consoleId(console.getId())
                .kind(request == null || request.kind() == null ? DeviceMaintenanceKind.REPAIR : request.kind())
                .note(note)
                .startedAt(now)
                .staffId(access.staffId())
                .deleted(false)
                .build());
        return catalogService.toResponse(console);
    }

    @Transactional
    public ConsoleDtos.ConsoleResponse end(Long consoleId, ConsoleDtos.MaintenanceEndRequest request) {
        GameConsole console = lock(consoleId);
        if (!console.isMaintenance()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cihaz bakımda değil");
        }
        LocalDateTime now = AppTime.nowLocal();
        DeviceMaintenanceLog log = logRepository
                .findFirstByConsoleIdAndEndedAtIsNullAndDeletedFalseOrderByStartedAtDesc(console.getId())
                .orElseGet(() -> DeviceMaintenanceLog.builder()
                        .merchantId(console.getMerchantId())
                        .branchId(console.getBranchId())
                        .consoleId(console.getId())
                        .kind(DeviceMaintenanceKind.REPAIR)
                        .note(console.getMaintenanceNote())
                        .startedAt(console.getMaintenanceSince() == null ? now : console.getMaintenanceSince())
                        .deleted(false)
                        .build());
        log.setEndedAt(now);
        if (request != null && request.cost() != null) {
            log.setCost(request.cost().setScale(2, RoundingMode.HALF_UP));
        }
        String closing = DeviceMappers.blankToNull(request == null ? null : request.note());
        if (closing != null) {
            String note = log.getNote() == null ? closing : log.getNote() + " · " + closing;
            log.setNote(note.substring(0, Math.min(note.length(), 255)));
        }
        logRepository.save(log);
        console.setMaintenance(false);
        console.setMaintenanceNote(null);
        console.setMaintenanceSince(null);
        consoleRepository.save(console);
        return catalogService.toResponse(console);
    }

    /** A cost with no downtime, e.g. a new controller. */
    @Transactional
    public ConsoleDtos.MaintenanceLogResponse expense(Long consoleId, ConsoleDtos.ExpenseRequest request) {
        GameConsole console = catalogService.requireOwned(consoleId);
        LocalDateTime now = AppTime.nowLocal();
        DeviceMaintenanceLog saved = logRepository.save(DeviceMaintenanceLog.builder()
                .merchantId(console.getMerchantId())
                .branchId(console.getBranchId())
                .consoleId(console.getId())
                .kind(request.kind() == null ? DeviceMaintenanceKind.EXPENSE : request.kind())
                .note(DeviceMappers.blankToNull(request.note()))
                .cost(request.cost().setScale(2, RoundingMode.HALF_UP))
                .startedAt(now)
                .endedAt(now)
                .staffId(access.staffId())
                .deleted(false)
                .build());
        return log(saved);
    }

    @Transactional(readOnly = true)
    public List<ConsoleDtos.MaintenanceLogResponse> logs(Long consoleId) {
        GameConsole console = catalogService.requireOwned(consoleId);
        return logRepository.findByConsoleIdAndDeletedFalseOrderByStartedAtDescIdDesc(console.getId()).stream()
                .map(DeviceMaintenanceService::log)
                .toList();
    }

    private GameConsole lock(Long consoleId) {
        GameConsole console = consoleRepository.lockOwned(consoleId, access.merchantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cihaz bulunamadı"));
        access.checkBranch(console.getBranchId());
        return console;
    }

    static ConsoleDtos.MaintenanceLogResponse log(DeviceMaintenanceLog log) {
        return new ConsoleDtos.MaintenanceLogResponse(
                log.getId(),
                log.getConsoleId(),
                log.getKind(),
                log.getNote(),
                log.getCost(),
                log.getStartedAt(),
                log.getEndedAt()
        );
    }
}
