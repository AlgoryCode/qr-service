package com.ael.algoryqrservice.qrservice.console.service;

import com.ael.algoryqrservice.qrservice.console.domain.DeviceRentalPricing;
import com.ael.algoryqrservice.qrservice.console.dto.ConsoleDtos;
import com.ael.algoryqrservice.qrservice.model.Branch;
import com.ael.algoryqrservice.qrservice.model.DeviceGame;
import com.ael.algoryqrservice.qrservice.model.DevicePackage;
import com.ael.algoryqrservice.qrservice.model.GameConsole;
import com.ael.algoryqrservice.qrservice.repository.DeviceGameRepository;
import com.ael.algoryqrservice.qrservice.repository.DevicePackageRepository;
import com.ael.algoryqrservice.qrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** A branch's game library (which games are on which device) and its fixed-price packages. */
@Service
@RequiredArgsConstructor
public class DeviceLibraryService {

    private final DeviceGameRepository gameRepository;
    private final DevicePackageRepository packageRepository;
    private final com.ael.algoryqrservice.qrservice.repository.GameConsoleRepository consoleRepository;
    private final ConsoleCatalogService catalogService;
    private final ConsoleAccess access;

    // ---- Games -------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ConsoleDtos.GameResponse> listGames(Long branchId) {
        Branch branch = access.requireBranch(branchId);
        List<DeviceGame> games = gameRepository.findByBranchIdAndMerchantIdAndDeletedFalseOrderByNameAscIdAsc(
                branch.getId(), branch.getMerchantId());
        Map<Long, List<Long>> consolesByGame = new HashMap<>();
        Map<Long, List<ConsoleDtos.GameRef>> byConsole = catalogService.gamesByConsole(
                consoleRepository.findByBranchIdAndMerchantIdAndDeletedFalseOrderByNameAscIdAsc(branch.getId(), branch.getMerchantId())
                        .stream().map(GameConsole::getId).toList());
        byConsole.forEach((consoleId, refs) -> refs.forEach(ref ->
                consolesByGame.computeIfAbsent(ref.id(), id -> new ArrayList<>()).add(consoleId)));
        return games.stream()
                .map(game -> game(game, consolesByGame.getOrDefault(game.getId(), List.of())))
                .toList();
    }

    @Transactional
    public ConsoleDtos.GameResponse createGame(Long branchId, ConsoleDtos.GameRequest request) {
        Branch branch = access.requireBranch(branchId);
        DeviceGame game = DeviceGame.builder()
                .merchantId(branch.getMerchantId())
                .branchId(branch.getId())
                .deleted(false)
                .build();
        apply(game, request);
        return game(gameRepository.save(game), List.of());
    }

    @Transactional
    public ConsoleDtos.GameResponse updateGame(Long gameId, ConsoleDtos.GameRequest request) {
        DeviceGame game = requireGame(gameId);
        apply(game, request);
        return game(gameRepository.save(game), List.of());
    }

    @Transactional
    public void deleteGame(Long gameId) {
        DeviceGame game = requireGame(gameId);
        gameRepository.unlinkGame(game.getId());
        game.setDeleted(true);
        gameRepository.save(game);
    }

    /** Replaces the games installed on a device; every game must be in the device's branch. */
    @Transactional
    public ConsoleDtos.ConsoleResponse setConsoleGames(Long consoleId, ConsoleDtos.ConsoleGamesRequest request) {
        GameConsole console = catalogService.requireOwned(consoleId);
        Set<Long> ids = new LinkedHashSet<>(request.gameIds());
        if (!ids.isEmpty()) {
            List<DeviceGame> games = gameRepository.findByIdIn(ids);
            boolean valid = games.size() == ids.size() && games.stream().allMatch(g -> !g.isDeleted()
                    && Objects.equals(g.getMerchantId(), console.getMerchantId())
                    && Objects.equals(g.getBranchId(), console.getBranchId()));
            if (!valid) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Oyunlar bu şubenin kütüphanesinden seçilmeli");
            }
        }
        gameRepository.unlinkConsole(console.getId());
        ids.forEach(gameId -> gameRepository.link(console.getId(), gameId));
        return catalogService.toResponse(console);
    }

    /** The game picked when starting a session; it must be in the device's branch. */
    public DeviceGame requireGameFor(GameConsole console, Long gameId) {
        return gameRepository.findByIdAndMerchantIdAndDeletedFalse(gameId, console.getMerchantId())
                .filter(game -> Objects.equals(game.getBranchId(), console.getBranchId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Oyun bulunamadı"));
    }

    private DeviceGame requireGame(Long gameId) {
        DeviceGame game = gameRepository.findByIdAndMerchantIdAndDeletedFalse(gameId, access.merchantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Oyun bulunamadı"));
        access.checkBranch(game.getBranchId());
        return game;
    }

    private static void apply(DeviceGame game, ConsoleDtos.GameRequest request) {
        game.setName(request.name().trim());
        game.setGenre(DeviceMappers.blankToNull(request.genre()));
        game.setMaxPlayers(request.maxPlayers());
    }

    private static ConsoleDtos.GameResponse game(DeviceGame game, List<Long> consoleIds) {
        return new ConsoleDtos.GameResponse(game.getId(), game.getBranchId(), game.getName(), game.getGenre(),
                game.getMaxPlayers(), consoleIds);
    }

    // ---- Packages ----------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ConsoleDtos.PackageResponse> listPackages(Long branchId, boolean activeOnly) {
        Branch branch = access.requireBranch(branchId);
        LocalDateTime now = AppTime.nowLocal();
        return packageRepository.findByBranchIdAndMerchantIdAndDeletedFalseOrderByPriceAscIdAsc(branch.getId(), branch.getMerchantId())
                .stream()
                .filter(p -> !activeOnly || p.isActive())
                .map(p -> pkg(p, now))
                .toList();
    }

    @Transactional
    public ConsoleDtos.PackageResponse createPackage(Long branchId, ConsoleDtos.PackageRequest request) {
        Branch branch = access.requireBranch(branchId);
        DevicePackage pkg = DevicePackage.builder()
                .merchantId(branch.getMerchantId())
                .branchId(branch.getId())
                .deleted(false)
                .build();
        apply(pkg, request);
        return pkg(packageRepository.save(pkg), AppTime.nowLocal());
    }

    @Transactional
    public ConsoleDtos.PackageResponse updatePackage(Long packageId, ConsoleDtos.PackageRequest request) {
        DevicePackage pkg = requirePackage(packageId);
        apply(pkg, request);
        return pkg(packageRepository.save(pkg), AppTime.nowLocal());
    }

    @Transactional
    public void deletePackage(Long packageId) {
        DevicePackage pkg = requirePackage(packageId);
        pkg.setDeleted(true);
        packageRepository.save(pkg);
    }

    /** The package chosen when starting on {@code console}; it must be on sale for that device now. */
    public DevicePackage requirePackageFor(GameConsole console, Long packageId, LocalDateTime now) {
        DevicePackage pkg = packageRepository.findByIdAndMerchantIdAndDeletedFalse(packageId, console.getMerchantId())
                .filter(p -> Objects.equals(p.getBranchId(), console.getBranchId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paket bulunamadı"));
        if (!pkg.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Paket satışta değil");
        }
        if (pkg.getDeviceType() != null && pkg.getDeviceType() != console.getDeviceType()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paket bu cihaz türü için değil");
        }
        if (!availableAt(pkg, now)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Paket şu saatte başlatılamaz");
        }
        return pkg;
    }

    /** Within the package's days and (when set) its start window. */
    static boolean availableAt(DevicePackage pkg, LocalDateTime at) {
        if (pkg.getStartTime() == null || pkg.getEndTime() == null) {
            return (pkg.getDaysMask() & (1 << (at.getDayOfWeek().getValue() - 1))) != 0;
        }
        // A tariff without a device type checks only the days and the time window.
        return new DeviceRentalPricing.Tariff(null, null, pkg.getDaysMask(), pkg.getStartTime(), pkg.getEndTime(),
                java.math.BigDecimal.ZERO).covers(at, null);
    }

    private DevicePackage requirePackage(Long packageId) {
        DevicePackage pkg = packageRepository.findByIdAndMerchantIdAndDeletedFalse(packageId, access.merchantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paket bulunamadı"));
        access.checkBranch(pkg.getBranchId());
        return pkg;
    }

    private static void apply(DevicePackage pkg, ConsoleDtos.PackageRequest request) {
        if ((request.startTime() == null) != (request.endTime() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saat aralığının başlangıcını ve bitişini birlikte girin");
        }
        if (request.startTime() != null && request.startTime().equals(request.endTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Başlangıç ve bitiş saati aynı olamaz");
        }
        pkg.setName(request.name().trim());
        pkg.setDeviceType(request.deviceType());
        pkg.setMinutes(request.minutes());
        pkg.setPrice(request.price().setScale(2, RoundingMode.HALF_UP));
        pkg.setDaysMask(request.days() == null || request.days().isEmpty() ? 127 : DeviceMappers.daysMask(request.days()));
        pkg.setStartTime(request.startTime() == null ? null : request.startTime().withSecond(0).withNano(0));
        pkg.setEndTime(request.endTime() == null ? null : request.endTime().withSecond(0).withNano(0));
        pkg.setActive(request.active() == null || request.active());
    }

    static ConsoleDtos.PackageResponse pkg(DevicePackage p, LocalDateTime now) {
        return new ConsoleDtos.PackageResponse(
                p.getId(),
                p.getBranchId(),
                p.getName(),
                p.getDeviceType(),
                p.getMinutes(),
                p.getPrice(),
                DeviceMappers.days(p.getDaysMask()),
                p.getStartTime(),
                p.getEndTime(),
                p.isActive(),
                p.isActive() && availableAt(p, now)
        );
    }
}
