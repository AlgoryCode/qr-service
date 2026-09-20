package com.ael.algoryqrservice.print.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.exception.UnauthorizedException;
import com.ael.algoryqrservice.model.Branch;
import com.ael.algoryqrservice.print.dto.PrintAgentDtos;
import com.ael.algoryqrservice.print.model.PrintAgentDevice;
import com.ael.algoryqrservice.print.model.PrintJob;
import com.ael.algoryqrservice.print.model.PrintJobStatus;
import com.ael.algoryqrservice.print.model.PrintPairingCode;
import com.ael.algoryqrservice.print.repository.PrintAgentDeviceRepository;
import com.ael.algoryqrservice.print.repository.PrintJobRepository;
import com.ael.algoryqrservice.print.repository.PrintPairingCodeRepository;
import com.ael.algoryqrservice.print.security.PrintDevicePrincipal;
import com.ael.algoryqrservice.repository.BranchRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PrintAgentDeviceService {

    private static final int PAIRING_TTL_MINUTES = 10;

    private final PrintAgentDeviceRepository deviceRepository;
    private final PrintPairingCodeRepository pairingCodeRepository;
    private final PrintJobRepository printJobRepository;
    private final PrintJobService printJobService;
    private final PrintAgentTokenService tokenService;
    private final BranchRepository branchRepository;
    private final SecurityUtils securityUtils;

    @Transactional
    public PrintAgentDtos.PairingCodeResponse createPairingCode(Long branchId) {
        Long userId = securityUtils.getCurrentUserId();
        Branch branch = requireOwnedBranch(branchId, userId);
        String code = tokenService.generatePairingCode();
        LocalDateTime now = LocalDateTime.now();
        pairingCodeRepository.save(PrintPairingCode.builder()
                .ownerUserId(userId)
                .branchId(branch.getId())
                .codeHash(tokenService.hashToken(code))
                .expiresAt(now.plusMinutes(PAIRING_TTL_MINUTES))
                .build());
        return PrintAgentDtos.PairingCodeResponse.builder()
                .code(code)
                .branchId(branch.getId())
                .expiresAt(now.plusMinutes(PAIRING_TTL_MINUTES))
                .build();
    }

    @Transactional
    public PrintAgentDtos.PairDeviceResponse pairDevice(PrintAgentDtos.PairDeviceRequest request) {
        LocalDateTime now = LocalDateTime.now();
        String code = request.getCode().trim();
        PrintPairingCode pairing = pairingCodeRepository.findByCodeHash(tokenService.hashToken(code))
                .orElseThrow(() -> new BadRequestException("Gecersiz eslestirme kodu"));
        if (!pairing.isUsable(now)) {
            throw new BadRequestException("Eslestirme kodu suresi dolmus veya kullanilmis");
        }
        String deviceToken = tokenService.generateDeviceToken();
        PrintAgentDevice device = deviceRepository.save(PrintAgentDevice.builder()
                .ownerUserId(pairing.getOwnerUserId())
                .branchId(pairing.getBranchId())
                .deviceName(request.getDeviceName().trim())
                .deviceTokenHash(tokenService.hashToken(deviceToken))
                .enabled(true)
                .lastSeenAt(now)
                .build());
        ensurePrintKitchenEnabled(pairing.getBranchId());
        pairing.setUsedAt(now);
        pairingCodeRepository.save(pairing);
        return PrintAgentDtos.PairDeviceResponse.builder()
                .userId(device.getOwnerUserId())
                .deviceId(device.getId())
                .branchId(device.getBranchId())
                .deviceToken(deviceToken)
                .apiKey(deviceToken)
                .deviceName(device.getDeviceName())
                .build();
    }

    @Transactional(readOnly = true)
    public PrintAgentDtos.SettingsResponse getSettings(Long branchId) {
        Long userId = securityUtils.getCurrentUserId();
        Branch branch = requireOwnedBranch(branchId, userId);
        return PrintAgentDtos.SettingsResponse.builder()
                .userId(userId)
                .branchId(branch.getId())
                .printKitchenEnabled(branch.isPrintKitchenEnabled())
                .devices(deviceRepository.findByOwnerUserIdAndBranchIdOrderByIdDesc(userId, branchId).stream()
                        .map(this::toDeviceResponse)
                        .toList())
                .build();
    }

    @Transactional
    public PrintAgentDtos.ApiKeyResponse createApiKey(PrintAgentDtos.CreateApiKeyRequest request) {
        Long userId = securityUtils.getCurrentUserId();
        Branch branch = requireOwnedBranch(request.getBranchId(), userId);
        String deviceName = request.getDeviceName() == null || request.getDeviceName().isBlank()
                ? "Print Agent"
                : request.getDeviceName().trim();
        String apiKey = tokenService.generateDeviceToken();
        PrintAgentDevice device = deviceRepository.save(PrintAgentDevice.builder()
                .ownerUserId(userId)
                .branchId(branch.getId())
                .deviceName(deviceName)
                .deviceTokenHash(tokenService.hashToken(apiKey))
                .enabled(true)
                .lastSeenAt(LocalDateTime.now())
                .build());
        ensurePrintKitchenEnabled(branch.getId());
        return PrintAgentDtos.ApiKeyResponse.builder()
                .userId(userId)
                .deviceId(device.getId())
                .branchId(branch.getId())
                .apiKey(apiKey)
                .deviceName(device.getDeviceName())
                .build();
    }

    @Transactional
    public PrintAgentDtos.ConnectResponse connect(PrintAgentDtos.ConnectRequest request) {
        String apiKey = request.getApiKey().trim();
        PrintAgentDevice device = deviceRepository
                .findByDeviceTokenHashAndEnabledTrue(tokenService.hashToken(apiKey))
                .orElseThrow(() -> new UnauthorizedException("API key gecersiz"));
        if (!device.getOwnerUserId().equals(request.getUserId())) {
            throw new UnauthorizedException("Kullanici ID eslesmiyor");
        }
        Branch branch = branchRepository.findByIdAndUserIdAndDeletedFalse(request.getBranchId(), request.getUserId())
                .orElseThrow(() -> new NotFoundException("Sube bulunamadi"));
        device.setBranchId(branch.getId());
        device.setLastSeenAt(LocalDateTime.now());
        deviceRepository.save(device);
        ensurePrintKitchenEnabled(branch.getId());
        return PrintAgentDtos.ConnectResponse.builder()
                .userId(device.getOwnerUserId())
                .deviceId(device.getId())
                .branchId(device.getBranchId())
                .deviceName(device.getDeviceName())
                .connected(true)
                .build();
    }

    @Transactional(readOnly = true)
    public PrintAgentDtos.PendingJobsResponse listJobs(int limit) {
        PrintAgentDevice device = requireCurrentDevice();
        int capped = Math.min(Math.max(limit, 1), 100);
        List<PrintAgentDtos.JobResponse> jobs = printJobRepository
                .findByBranchIdOrderByCreatedAtDesc(device.getBranchId())
                .stream()
                .limit(capped)
                .map(this::toJobResponse)
                .toList();
        return PrintAgentDtos.PendingJobsResponse.builder().jobs(jobs).build();
    }

    @Transactional(readOnly = true)
    public List<PrintAgentDtos.DeviceResponse> listDevices(Long branchId) {
        Long userId = securityUtils.getCurrentUserId();
        requireOwnedBranch(branchId, userId);
        return deviceRepository.findByOwnerUserIdAndBranchIdOrderByIdDesc(userId, branchId).stream()
                .map(this::toDeviceResponse)
                .toList();
    }

    @Transactional
    public PrintAgentDtos.DeviceResponse setDeviceEnabled(Long deviceId, boolean enabled) {
        Long userId = securityUtils.getCurrentUserId();
        PrintAgentDevice device = deviceRepository.findByIdAndOwnerUserId(deviceId, userId)
                .orElseThrow(() -> new NotFoundException("Yazici cihaz bulunamadi"));
        device.setEnabled(enabled);
        return toDeviceResponse(deviceRepository.save(device));
    }

    @Transactional
    public void heartbeat(PrintAgentDtos.HeartbeatRequest request) {
        PrintAgentDevice device = requireCurrentDevice();
        device.setLastSeenAt(LocalDateTime.now());
        if (request.getPrinterName() != null) {
            device.setPrinterName(trimToNull(request.getPrinterName()));
        }
        if (request.getAgentVersion() != null) {
            device.setAgentVersion(trimToNull(request.getAgentVersion()));
        }
        deviceRepository.save(device);
    }

    @Transactional
    public PrintAgentDtos.PendingJobsResponse claimPending(int limit) {
        return printJobService.claimPending(requireCurrentDevice(), limit);
    }

    @Transactional
    public void ack(Long jobId, PrintAgentDtos.AckRequest request) {
        printJobService.ack(requireCurrentDevice(), jobId, request);
    }

    @Transactional
    public PrintAgentDtos.JobResponse retryJobForOwner(Long jobId) {
        Long userId = securityUtils.getCurrentUserId();
        PrintJob job = printJobRepository.findByIdAndOwnerUserId(jobId, userId)
                .orElseThrow(() -> new NotFoundException("Print job bulunamadi"));
        return printJobService.requeue(job);
    }

    @Transactional
    public PrintAgentDtos.JobResponse reprintForDevice(Long jobId) {
        return printJobService.claimForReprint(requireCurrentDevice(), jobId);
    }

    @Transactional(readOnly = true)
    public List<PrintAgentDtos.JobResponse> listFailedJobs(Long branchId) {
        Long userId = securityUtils.getCurrentUserId();
        requireOwnedBranch(branchId, userId);
        return printJobRepository
                .findByOwnerUserIdAndBranchIdAndStatusOrderByCreatedAtDesc(userId, branchId, PrintJobStatus.FAILED)
                .stream()
                .limit(50)
                .map(this::toJobResponse)
                .toList();
    }

    @Transactional
    public Branch setPrintKitchenEnabled(Long branchId, boolean enabled) {
        Long userId = securityUtils.getCurrentUserId();
        Branch branch = requireOwnedBranch(branchId, userId);
        branch.setPrintKitchenEnabled(enabled);
        return branchRepository.save(branch);
    }

    public PrintAgentDevice requireCurrentDevice() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getDetails() instanceof PrintDevicePrincipal principal)) {
            throw new UnauthorizedException("Yazici cihaz oturumu gerekli");
        }
        return deviceRepository.findById(principal.deviceId())
                .filter(PrintAgentDevice::isEnabled)
                .orElseThrow(() -> new UnauthorizedException("Yazici cihaz aktif degil"));
    }

    private Branch requireOwnedBranch(Long branchId, Long userId) {
        return branchRepository.findByIdAndUserIdAndDeletedFalse(branchId, userId)
                .orElseThrow(() -> new NotFoundException("Sube bulunamadi"));
    }

    private void ensurePrintKitchenEnabled(Long branchId) {
        branchRepository.findById(branchId).ifPresent(branch -> {
            if (branch.isDeleted() || branch.isPrintKitchenEnabled()) {
                return;
            }
            branch.setPrintKitchenEnabled(true);
            branchRepository.save(branch);
        });
    }

    private PrintAgentDtos.JobResponse toJobResponse(com.ael.algoryqrservice.print.model.PrintJob job) {
        return PrintAgentDtos.JobResponse.builder()
                .id(job.getId())
                .sourceType(job.getSourceType())
                .sourceId(job.getSourceId())
                .jobType(job.getJobType())
                .status(job.getStatus())
                .payload(job.getPayloadJson())
                .attempts(job.getAttempts())
                .lastError(job.getLastError())
                .createdAt(job.getCreatedAt())
                .build();
    }

    private PrintAgentDtos.DeviceResponse toDeviceResponse(PrintAgentDevice device) {
        return PrintAgentDtos.DeviceResponse.builder()
                .id(device.getId())
                .branchId(device.getBranchId())
                .deviceName(device.getDeviceName())
                .printerName(device.getPrinterName())
                .agentVersion(device.getAgentVersion())
                .lastSeenAt(device.getLastSeenAt())
                .enabled(device.isEnabled())
                .createdAt(device.getCreatedAt())
                .build();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
