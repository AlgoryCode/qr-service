package com.ael.algoryqrservice.qrservice.console.service;

import com.ael.algoryqrservice.qrservice.branch.service.BranchService;
import com.ael.algoryqrservice.qrservice.console.domain.ConsoleRentalPricing;
import com.ael.algoryqrservice.qrservice.console.dto.ConsoleDtos;
import com.ael.algoryqrservice.qrservice.model.ConsoleRateSetting;
import com.ael.algoryqrservice.qrservice.model.GameConsole;
import com.ael.algoryqrservice.qrservice.repository.ConsoleRateSettingRepository;
import com.ael.algoryqrservice.qrservice.repository.ConsoleSessionRepository;
import com.ael.algoryqrservice.qrservice.repository.GameConsoleRepository;
import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

/** The consoles a branch rents out and the hourly rates: a branch default, overridable per console. */
@Service
@RequiredArgsConstructor
public class ConsoleCatalogService {

    private final GameConsoleRepository consoleRepository;
    private final ConsoleRateSettingRepository rateSettingRepository;
    private final ConsoleSessionRepository sessionRepository;
    private final BranchService branchService;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public List<ConsoleDtos.ConsoleResponse> list(Long branchId) {
        var branch = branchService.requireOwned(branchId);
        BigDecimal defaultRate = defaultRate(branch.getId(), branch.getMerchantId());
        return consoleRepository
                .findByBranchIdAndMerchantIdAndDeletedFalseOrderByNameAscIdAsc(branch.getId(), branch.getMerchantId())
                .stream()
                .map(console -> toResponse(console, defaultRate))
                .toList();
    }

    @Transactional
    public ConsoleDtos.ConsoleResponse create(Long branchId, ConsoleDtos.ConsoleRequest request) {
        var branch = branchService.requireOwned(branchId);
        GameConsole saved = consoleRepository.save(GameConsole.builder()
                .merchantId(branch.getMerchantId())
                .branchId(branch.getId())
                .name(request.name().trim())
                .hourlyRate(ConsoleRentalPricing.scaleRate(request.hourlyRate()))
                .deleted(false)
                .build());
        return toResponse(saved, defaultRate(saved.getBranchId(), saved.getMerchantId()));
    }

    @Transactional
    public ConsoleDtos.ConsoleResponse update(Long consoleId, ConsoleDtos.ConsoleRequest request) {
        GameConsole console = requireOwned(consoleId);
        console.setName(request.name().trim());
        console.setHourlyRate(ConsoleRentalPricing.scaleRate(request.hourlyRate()));
        GameConsole saved = consoleRepository.save(console);
        return toResponse(saved, defaultRate(saved.getBranchId(), saved.getMerchantId()));
    }

    @Transactional
    public void delete(Long consoleId) {
        GameConsole console = requireOwned(consoleId);
        if (sessionRepository.existsByConsoleIdAndEndedAtIsNullAndDeletedFalse(console.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Konsolda açık oturum var, önce bitirin");
        }
        console.setDeleted(true);
        consoleRepository.save(console);
    }

    @Transactional(readOnly = true)
    public ConsoleDtos.RateSettingResponse getRate(Long branchId) {
        var branch = branchService.requireOwned(branchId);
        return new ConsoleDtos.RateSettingResponse(branch.getId(), defaultRate(branch.getId(), branch.getMerchantId()));
    }

    @Transactional
    public ConsoleDtos.RateSettingResponse updateRate(Long branchId, ConsoleDtos.RateSettingRequest request) {
        var branch = branchService.requireOwned(branchId);
        ConsoleRateSetting setting = rateSettingRepository
                .findByBranchIdAndMerchantId(branch.getId(), branch.getMerchantId())
                .orElseGet(() -> ConsoleRateSetting.builder()
                        .merchantId(branch.getMerchantId())
                        .branchId(branch.getId())
                        .deleted(false)
                        .build());
        setting.setDefaultHourlyRate(ConsoleRentalPricing.scaleRate(request.defaultHourlyRate()));
        ConsoleRateSetting saved = rateSettingRepository.save(setting);
        return new ConsoleDtos.RateSettingResponse(saved.getBranchId(), saved.getDefaultHourlyRate());
    }

    /** The console's own rate, else the branch default, else null when neither is set. */
    BigDecimal effectiveRate(GameConsole console) {
        if (console.getHourlyRate() != null) {
            return console.getHourlyRate();
        }
        return defaultRate(console.getBranchId(), console.getMerchantId());
    }

    GameConsole requireOwned(Long consoleId) {
        Long merchantId = securityUtils.getCurrentMerchantId();
        return consoleRepository.findByIdAndMerchantIdAndDeletedFalse(consoleId, merchantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Konsol bulunamadı"));
    }

    private BigDecimal defaultRate(Long branchId, Long merchantId) {
        return rateSettingRepository.findByBranchIdAndMerchantId(branchId, merchantId)
                .filter(setting -> !setting.isDeleted())
                .map(ConsoleRateSetting::getDefaultHourlyRate)
                .orElse(null);
    }

    private ConsoleDtos.ConsoleResponse toResponse(GameConsole console, BigDecimal defaultRate) {
        return new ConsoleDtos.ConsoleResponse(
                console.getId(),
                console.getBranchId(),
                console.getName(),
                console.getHourlyRate(),
                console.getHourlyRate() != null ? console.getHourlyRate() : defaultRate,
                sessionRepository.existsByConsoleIdAndEndedAtIsNullAndDeletedFalse(console.getId())
        );
    }
}
