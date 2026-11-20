package com.ael.algoryqrservice.qrservice.console.service;

import com.ael.algoryqrservice.qrservice.console.domain.ConsoleRentalPricing;
import com.ael.algoryqrservice.qrservice.console.domain.DeviceRentalPricing;
import com.ael.algoryqrservice.qrservice.console.domain.DeviceReservationStatus;
import com.ael.algoryqrservice.qrservice.console.domain.DeviceType;
import com.ael.algoryqrservice.qrservice.console.dto.ConsoleDtos;
import com.ael.algoryqrservice.qrservice.model.Branch;
import com.ael.algoryqrservice.qrservice.model.ConsoleRateSetting;
import com.ael.algoryqrservice.qrservice.model.DeviceGame;
import com.ael.algoryqrservice.qrservice.model.DeviceReservation;
import com.ael.algoryqrservice.qrservice.model.DeviceTariffRule;
import com.ael.algoryqrservice.qrservice.model.DeviceTypeRate;
import com.ael.algoryqrservice.qrservice.model.GameConsole;
import com.ael.algoryqrservice.qrservice.repository.ConsoleRateSettingRepository;
import com.ael.algoryqrservice.qrservice.repository.ConsoleSessionRepository;
import com.ael.algoryqrservice.qrservice.repository.DeviceGameRepository;
import com.ael.algoryqrservice.qrservice.repository.DeviceReservationRepository;
import com.ael.algoryqrservice.qrservice.repository.DeviceTariffRuleRepository;
import com.ael.algoryqrservice.qrservice.repository.DeviceTypeRateRepository;
import com.ael.algoryqrservice.qrservice.repository.GameConsoleRepository;
import com.ael.algoryqrservice.qrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The devices a branch rents out, its rates and charging rules. A device's hourly rate is its own, else the
 * branch's rate for its type, else the branch default; the 4-controller rate follows the same chain and falls back
 * to the normal rate.
 */
@Service
@RequiredArgsConstructor
public class ConsoleCatalogService {

    /** From this many controllers on, the 4-controller rate applies. */
    public static final int MULTI_CONTROLLER_THRESHOLD = 3;

    private final GameConsoleRepository consoleRepository;
    private final ConsoleRateSettingRepository rateSettingRepository;
    private final ConsoleSessionRepository sessionRepository;
    private final DeviceTypeRateRepository typeRateRepository;
    private final DeviceTariffRuleRepository tariffRepository;
    private final DeviceReservationRepository reservationRepository;
    private final ConsoleAccess access;
    private final DeviceGameRepository gameRepository;

    // ---- Devices -----------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ConsoleDtos.ConsoleResponse> list(Long branchId) {
        Branch branch = access.requireBranch(branchId);
        RateContext rates = rateContext(branch.getId(), branch.getMerchantId());
        List<GameConsole> consoles = consoleRepository
                .findByBranchIdAndMerchantIdAndDeletedFalseOrderByNameAscIdAsc(branch.getId(), branch.getMerchantId());
        Map<Long, String> names = new HashMap<>();
        consoles.forEach(c -> names.put(c.getId(), c.getName()));
        LocalDateTime now = AppTime.nowLocal();
        Map<Long, DeviceReservation> next = new HashMap<>();
        for (DeviceReservation r : reservationRepository.findOverlappingForBranch(
                branch.getId(), branch.getMerchantId(), now, now.plusHours(24))) {
            if (r.getStatus() == DeviceReservationStatus.BOOKED) {
                next.putIfAbsent(r.getConsoleId(), r);
            }
        }
        Map<Long, List<ConsoleDtos.GameRef>> games = gamesByConsole(consoles.stream().map(GameConsole::getId).toList());
        return consoles.stream()
                .map(console -> toResponse(console, rates,
                        Optional.ofNullable(next.get(console.getId()))
                                .map(r -> DeviceMappers.reservation(r, names.get(r.getConsoleId())))
                                .orElse(null),
                        games.getOrDefault(console.getId(), List.of())))
                .toList();
    }

    @Transactional
    public ConsoleDtos.ConsoleResponse create(Long branchId, ConsoleDtos.ConsoleRequest request) {
        Branch branch = access.requireBranch(branchId);
        GameConsole saved = consoleRepository.save(GameConsole.builder()
                .merchantId(branch.getMerchantId())
                .branchId(branch.getId())
                .name(request.name().trim())
                .deviceType(request.deviceType() == null ? DeviceType.PLAYSTATION : request.deviceType())
                .hourlyRate(ConsoleRentalPricing.scaleRate(request.hourlyRate()))
                .multiHourlyRate(ConsoleRentalPricing.scaleRate(request.multiHourlyRate()))
                .purchaseCost(ConsoleRentalPricing.scaleRate(request.purchaseCost()))
                .purchasedOn(request.purchasedOn())
                .publicToken(DeviceMappers.newToken(24))
                .deleted(false)
                .build());
        return toResponse(saved);
    }

    @Transactional
    public ConsoleDtos.ConsoleResponse update(Long consoleId, ConsoleDtos.ConsoleRequest request) {
        GameConsole console = requireOwned(consoleId);
        console.setName(request.name().trim());
        if (request.deviceType() != null) {
            console.setDeviceType(request.deviceType());
        }
        console.setHourlyRate(ConsoleRentalPricing.scaleRate(request.hourlyRate()));
        console.setMultiHourlyRate(ConsoleRentalPricing.scaleRate(request.multiHourlyRate()));
        console.setPurchaseCost(ConsoleRentalPricing.scaleRate(request.purchaseCost()));
        console.setPurchasedOn(request.purchasedOn());
        return toResponse(consoleRepository.save(console));
    }

    /** A new QR key; the old QR stops working. */
    @Transactional
    public ConsoleDtos.ConsoleResponse rotateToken(Long consoleId) {
        GameConsole console = requireOwned(consoleId);
        console.setPublicToken(DeviceMappers.newToken(24));
        return toResponse(consoleRepository.save(console));
    }

    /** Refuses a device that is out of service. */
    public static void requireInService(GameConsole console) {
        if (console.isMaintenance()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cihaz bakımda; önce bakımı bitirin");
        }
    }

    public ConsoleDtos.ConsoleResponse toResponse(GameConsole console) {
        return toResponse(console, rateContext(console.getBranchId(), console.getMerchantId()), null,
                gamesByConsole(List.of(console.getId())).getOrDefault(console.getId(), List.of()));
    }

    public Map<Long, List<ConsoleDtos.GameRef>> gamesByConsole(List<Long> consoleIds) {
        if (consoleIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> links = gameRepository.findLinks(consoleIds);
        if (links.isEmpty()) {
            return Map.of();
        }
        Map<Long, DeviceGame> games = new HashMap<>();
        gameRepository.findByIdIn(links.stream().map(row -> ((Number) row[1]).longValue()).distinct().toList())
                .stream().filter(g -> !g.isDeleted()).forEach(g -> games.put(g.getId(), g));
        Map<Long, List<ConsoleDtos.GameRef>> result = new HashMap<>();
        for (Object[] row : links) {
            DeviceGame game = games.get(((Number) row[1]).longValue());
            if (game != null) {
                result.computeIfAbsent(((Number) row[0]).longValue(), id -> new ArrayList<>())
                        .add(new ConsoleDtos.GameRef(game.getId(), game.getName()));
            }
        }
        result.values().forEach(list -> list.sort(java.util.Comparator.comparing(ConsoleDtos.GameRef::name)));
        return result;
    }

    @Transactional
    public void delete(Long consoleId) {
        GameConsole console = requireOwned(consoleId);
        if (sessionRepository.existsByConsoleIdAndEndedAtIsNullAndDeletedFalse(console.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cihazda açık oturum var, önce bitirin");
        }
        console.setDeleted(true);
        consoleRepository.save(console);
    }

    // ---- Branch settings ---------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ConsoleDtos.RateSettingResponse getRate(Long branchId) {
        Branch branch = access.requireBranch(branchId);
        return toSettingResponse(branch.getId(), setting(branch.getId(), branch.getMerchantId()).orElse(null));
    }

    @Transactional
    public ConsoleDtos.RateSettingResponse updateRate(Long branchId, ConsoleDtos.RateSettingRequest request) {
        Branch branch = access.requireBranch(branchId);
        ConsoleRateSetting setting = rateSettingRepository
                .findByBranchIdAndMerchantId(branch.getId(), branch.getMerchantId())
                .orElseGet(() -> ConsoleRateSetting.builder()
                        .merchantId(branch.getMerchantId())
                        .branchId(branch.getId())
                        .deleted(false)
                        .build());
        setting.setDeleted(false);
        setting.setDefaultHourlyRate(ConsoleRentalPricing.scaleRate(request.defaultHourlyRate()));
        setting.setDefaultMultiHourlyRate(ConsoleRentalPricing.scaleRate(request.defaultMultiHourlyRate()));
        // Older clients only send the hourly rate (no rounding); leave the rules they do not know about as they are.
        if (request.roundingMinutes() != null) {
            boolean loyaltyOn = request.loyaltyEveryMinutes() != null && request.loyaltyRewardMinutes() != null;
            setting.setMinimumMinutes(request.minimumMinutes() == null ? 0 : request.minimumMinutes());
            setting.setRoundingMinutes(request.roundingMinutes());
            setting.setLoyaltyEveryMinutes(loyaltyOn ? request.loyaltyEveryMinutes() : null);
            setting.setLoyaltyRewardMinutes(loyaltyOn ? request.loyaltyRewardMinutes() : null);
        }
        ConsoleRateSetting saved = rateSettingRepository.save(setting);
        return toSettingResponse(saved.getBranchId(), saved);
    }

    @Transactional(readOnly = true)
    public List<ConsoleDtos.TypeRate> getTypeRates(Long branchId) {
        Branch branch = access.requireBranch(branchId);
        return typeRateRepository.findByBranchIdAndMerchantId(branch.getId(), branch.getMerchantId()).stream()
                .filter(rate -> !rate.isDeleted())
                .sorted((a, b) -> a.getDeviceType().compareTo(b.getDeviceType()))
                .map(rate -> new ConsoleDtos.TypeRate(rate.getDeviceType(), rate.getHourlyRate(), rate.getMultiHourlyRate()))
                .toList();
    }

    @Transactional
    public List<ConsoleDtos.TypeRate> updateTypeRates(Long branchId, ConsoleDtos.TypeRatesRequest request) {
        Branch branch = access.requireBranch(branchId);
        Map<DeviceType, DeviceTypeRate> existing = new EnumMap<>(DeviceType.class);
        typeRateRepository.findByBranchIdAndMerchantId(branch.getId(), branch.getMerchantId())
                .forEach(rate -> existing.put(rate.getDeviceType(), rate));
        Set<DeviceType> seen = new HashSet<>();
        List<DeviceTypeRate> toSave = new ArrayList<>();
        for (ConsoleDtos.TypeRate rate : request.rates()) {
            if (!seen.add(rate.deviceType())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Her cihaz türü bir kez girilebilir");
            }
            DeviceTypeRate row = existing.computeIfAbsent(rate.deviceType(), type -> DeviceTypeRate.builder()
                    .merchantId(branch.getMerchantId())
                    .branchId(branch.getId())
                    .deviceType(type)
                    .build());
            row.setHourlyRate(ConsoleRentalPricing.scaleRate(rate.hourlyRate()));
            row.setMultiHourlyRate(ConsoleRentalPricing.scaleRate(rate.multiHourlyRate()));
            row.setDeleted(rate.hourlyRate() == null && rate.multiHourlyRate() == null);
            toSave.add(row);
        }
        existing.forEach((type, row) -> {
            if (!seen.contains(type) && !row.isDeleted()) {
                row.setDeleted(true);
                toSave.add(row);
            }
        });
        typeRateRepository.saveAll(toSave);
        return getTypeRates(branchId);
    }

    // ---- Tariffs -----------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ConsoleDtos.TariffResponse> listTariffs(Long branchId) {
        Branch branch = access.requireBranch(branchId);
        return tariffRepository.findByBranchIdAndMerchantIdAndDeletedFalseOrderByIdAsc(branch.getId(), branch.getMerchantId())
                .stream()
                .map(DeviceMappers::tariff)
                .toList();
    }

    @Transactional
    public ConsoleDtos.TariffResponse createTariff(Long branchId, ConsoleDtos.TariffRequest request) {
        Branch branch = access.requireBranch(branchId);
        DeviceTariffRule rule = DeviceTariffRule.builder()
                .merchantId(branch.getMerchantId())
                .branchId(branch.getId())
                .deleted(false)
                .build();
        apply(rule, request);
        return DeviceMappers.tariff(tariffRepository.save(rule));
    }

    @Transactional
    public ConsoleDtos.TariffResponse updateTariff(Long tariffId, ConsoleDtos.TariffRequest request) {
        DeviceTariffRule rule = requireTariff(tariffId);
        apply(rule, request);
        return DeviceMappers.tariff(tariffRepository.save(rule));
    }

    @Transactional
    public void deleteTariff(Long tariffId) {
        DeviceTariffRule rule = requireTariff(tariffId);
        rule.setDeleted(true);
        tariffRepository.save(rule);
    }

    private DeviceTariffRule requireTariff(Long tariffId) {
        DeviceTariffRule rule = tariffRepository.findByIdAndMerchantIdAndDeletedFalse(tariffId, access.merchantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tarife bulunamadı"));
        access.checkBranch(rule.getBranchId());
        return rule;
    }

    private static void apply(DeviceTariffRule rule, ConsoleDtos.TariffRequest request) {
        if (request.startTime().equals(request.endTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Başlangıç ve bitiş saati aynı olamaz");
        }
        rule.setName(request.name().trim());
        rule.setDeviceType(request.deviceType());
        rule.setDaysMask(DeviceMappers.daysMask(request.days()));
        rule.setStartTime(request.startTime().withSecond(0).withNano(0));
        rule.setEndTime(request.endTime().withSecond(0).withNano(0));
        rule.setAdjustPercent(request.adjustPercent().setScale(2, java.math.RoundingMode.HALF_UP));
    }

    // ---- Rate resolution (used by sessions) --------------------------------------------------------------------

    /** Rates and rules of one branch, loaded once per request. */
    public record RateContext(
            ConsoleRateSetting setting,
            Map<DeviceType, DeviceTypeRate> typeRates,
            DeviceRentalPricing.Rules rules
    ) {

        public BigDecimal rate(GameConsole console, int controllerCount) {
            if (controllerCount >= MULTI_CONTROLLER_THRESHOLD) {
                BigDecimal multi = multiRate(console);
                if (multi != null) {
                    return multi;
                }
            }
            return normalRate(console);
        }

        BigDecimal normalRate(GameConsole console) {
            if (console.getHourlyRate() != null) {
                return console.getHourlyRate();
            }
            DeviceTypeRate type = typeRates.get(console.getDeviceType());
            if (type != null && type.getHourlyRate() != null) {
                return type.getHourlyRate();
            }
            return setting == null ? null : setting.getDefaultHourlyRate();
        }

        BigDecimal multiRate(GameConsole console) {
            if (console.getMultiHourlyRate() != null) {
                return console.getMultiHourlyRate();
            }
            DeviceTypeRate type = typeRates.get(console.getDeviceType());
            if (type != null && type.getMultiHourlyRate() != null) {
                return type.getMultiHourlyRate();
            }
            return setting == null ? null : setting.getDefaultMultiHourlyRate();
        }
    }

    public RateContext rateContext(Long branchId, Long merchantId) {
        ConsoleRateSetting setting = setting(branchId, merchantId).orElse(null);
        Map<DeviceType, DeviceTypeRate> types = new EnumMap<>(DeviceType.class);
        typeRateRepository.findByBranchIdAndMerchantId(branchId, merchantId).stream()
                .filter(rate -> !rate.isDeleted())
                .forEach(rate -> types.put(rate.getDeviceType(), rate));
        List<DeviceRentalPricing.Tariff> tariffs = tariffRepository
                .findByBranchIdAndMerchantIdAndDeletedFalseOrderByIdAsc(branchId, merchantId).stream()
                .map(rule -> new DeviceRentalPricing.Tariff(rule.getId(), rule.getDeviceType(), rule.getDaysMask(),
                        rule.getStartTime(), rule.getEndTime(), rule.getAdjustPercent()))
                .toList();
        DeviceRentalPricing.Rules rules = new DeviceRentalPricing.Rules(
                setting == null ? 0 : setting.getMinimumMinutes(),
                setting == null ? 1 : setting.getRoundingMinutes(),
                tariffs
        );
        return new RateContext(setting, types, rules);
    }

    /** The console's normal rate; null when nothing is set anywhere. */
    BigDecimal effectiveRate(GameConsole console) {
        return rateContext(console.getBranchId(), console.getMerchantId()).normalRate(console);
    }

    public GameConsole requireOwned(Long consoleId) {
        GameConsole console = consoleRepository.findByIdAndMerchantIdAndDeletedFalse(consoleId, access.merchantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cihaz bulunamadı"));
        access.checkBranch(console.getBranchId());
        return console;
    }

    private Optional<ConsoleRateSetting> setting(Long branchId, Long merchantId) {
        return rateSettingRepository.findByBranchIdAndMerchantId(branchId, merchantId)
                .filter(setting -> !setting.isDeleted());
    }

    private static ConsoleDtos.RateSettingResponse toSettingResponse(Long branchId, ConsoleRateSetting setting) {
        if (setting == null) {
            return new ConsoleDtos.RateSettingResponse(branchId, null, null, 0, 1, null, null);
        }
        return new ConsoleDtos.RateSettingResponse(
                branchId,
                setting.getDefaultHourlyRate(),
                setting.getDefaultMultiHourlyRate(),
                setting.getMinimumMinutes(),
                setting.getRoundingMinutes(),
                setting.getLoyaltyEveryMinutes(),
                setting.getLoyaltyRewardMinutes()
        );
    }

    private ConsoleDtos.ConsoleResponse toResponse(
            GameConsole console,
            RateContext rates,
            ConsoleDtos.ReservationResponse nextReservation,
            List<ConsoleDtos.GameRef> games
    ) {
        BigDecimal normal = rates.normalRate(console);
        BigDecimal multi = rates.multiRate(console);
        return new ConsoleDtos.ConsoleResponse(
                console.getId(),
                console.getBranchId(),
                console.getName(),
                console.getDeviceType(),
                console.getHourlyRate(),
                console.getMultiHourlyRate(),
                normal,
                multi != null ? multi : normal,
                sessionRepository.existsByConsoleIdAndEndedAtIsNullAndDeletedFalse(console.getId()),
                nextReservation,
                console.getPublicToken(),
                console.isMaintenance(),
                console.getMaintenanceNote(),
                console.getMaintenanceSince(),
                console.getPurchaseCost(),
                console.getPurchasedOn(),
                games
        );
    }
}
