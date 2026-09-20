package com.ael.algoryqrservice.print.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.print.dto.PrintAgentDtos;
import com.ael.algoryqrservice.print.model.PrintAgentDevice;
import com.ael.algoryqrservice.print.model.PrintJob;
import com.ael.algoryqrservice.print.model.PrintJobStatus;
import com.ael.algoryqrservice.print.model.PrintJobType;
import com.ael.algoryqrservice.print.model.PrintSourceType;
import com.ael.algoryqrservice.print.repository.PrintAgentDeviceRepository;
import com.ael.algoryqrservice.print.repository.PrintJobRepository;
import com.ael.algoryqrservice.repository.BranchRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PrintJobServiceImpl implements PrintJobService {

    private static final int CLAIM_LEASE_SECONDS = 30;
    private static final int MAX_LIMIT = 20;

    private final PrintJobRepository printJobRepository;
    private final PrintAgentDeviceRepository printAgentDeviceRepository;
    private final BranchRepository branchRepository;

    @Override
    @Transactional
    public Optional<PrintJob> enqueueKitchenTicket(
            Long ownerUserId,
            Long branchId,
            PrintSourceType sourceType,
            String sourceId,
            JsonNode payload
    ) {
        if (ownerUserId == null || sourceType == null || sourceId == null || sourceId.isBlank() || payload == null) {
            return Optional.empty();
        }
        if (branchId == null) {
            List<Long> deviceBranches = printAgentDeviceRepository
                    .findByOwnerUserIdAndEnabledTrueOrderByLastSeenAtDescIdDesc(ownerUserId)
                    .stream()
                    .map(PrintAgentDevice::getBranchId)
                    .distinct()
                    .toList();
            if (!deviceBranches.isEmpty()) {
                Optional<PrintJob> last = Optional.empty();
                for (Long deviceBranchId : deviceBranches) {
                    last = persistKitchenTicket(ownerUserId, deviceBranchId, sourceType, sourceId, payload);
                }
                return last;
            }
            branchId = resolvePrintBranch(ownerUserId).orElse(null);
            if (branchId == null) {
                return Optional.empty();
            }
        } else if (!isPrintEnabled(branchId)
                && !printAgentDeviceRepository.existsByBranchIdAndEnabledTrue(branchId)) {
            return Optional.empty();
        }

        return persistKitchenTicket(ownerUserId, branchId, sourceType, sourceId, payload);
    }

    private Optional<PrintJob> persistKitchenTicket(
            Long ownerUserId,
            Long branchId,
            PrintSourceType sourceType,
            String sourceId,
            JsonNode payload
    ) {
        String idempotencyKey = sourceType.name() + ":" + sourceId + ":" + PrintJobType.KITCHEN_TICKET.name()
                + ":" + branchId;
        Optional<PrintJob> existing = printJobRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return existing;
        }

        PrintJob job = PrintJob.builder()
                .ownerUserId(ownerUserId)
                .branchId(branchId)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .jobType(PrintJobType.KITCHEN_TICKET)
                .status(PrintJobStatus.PENDING)
                .payloadJson(payload)
                .idempotencyKey(idempotencyKey)
                .attempts(0)
                .build();
        try {
            return Optional.of(printJobRepository.save(job));
        } catch (DataIntegrityViolationException exception) {
            return printJobRepository.findByIdempotencyKey(idempotencyKey);
        }
    }

    @Override
    @Transactional
    public PrintAgentDtos.PendingJobsResponse claimPending(PrintAgentDevice device, int limit) {
        LocalDateTime now = LocalDateTime.now();
        printJobRepository.releaseStaleClaims(now.minusSeconds(CLAIM_LEASE_SECONDS), now);

        int capped = Math.min(Math.max(limit, 1), MAX_LIMIT);
        List<PrintJob> pending = printJobRepository.findPendingForBranch(device.getBranchId());
        List<PrintAgentDtos.JobResponse> claimed = new ArrayList<>();
        for (PrintJob job : pending) {
            if (claimed.size() >= capped) {
                break;
            }
            int updated = printJobRepository.claimJob(job.getId(), device.getId(), now);
            if (updated != 1) {
                continue;
            }
            PrintJob refreshed = printJobRepository.findById(job.getId()).orElse(job);
            claimed.add(toJobResponse(refreshed));
        }
        return PrintAgentDtos.PendingJobsResponse.builder().jobs(claimed).build();
    }

    @Override
    @Transactional
    public void ack(PrintAgentDevice device, Long jobId, PrintAgentDtos.AckRequest request) {
        if (request.getStatus() != PrintJobStatus.DONE && request.getStatus() != PrintJobStatus.FAILED) {
            throw new BadRequestException("Ack status DONE veya FAILED olmali");
        }
        PrintJob job = printJobRepository.findByIdAndClaimedByDeviceId(jobId, device.getId())
                .orElseThrow(() -> new NotFoundException("Print job bulunamadi"));
        if (job.getStatus() != PrintJobStatus.CLAIMED) {
            throw new BadRequestException("Job claim durumunda degil");
        }
        LocalDateTime now = LocalDateTime.now();
        job.setStatus(request.getStatus());
        job.setLastError(trimToNull(request.getError()));
        job.setCompletedAt(now);
        if (request.getStatus() == PrintJobStatus.FAILED) {
            job.setStatus(PrintJobStatus.FAILED);
        }
        printJobRepository.save(job);
    }

    @Override
    @Transactional
    public PrintAgentDtos.JobResponse requeue(PrintJob job) {
        if (job.getStatus() != PrintJobStatus.FAILED && job.getStatus() != PrintJobStatus.DONE) {
            throw new BadRequestException("Sadece FAILED veya DONE job tekrar kuyruga alinabilir");
        }
        job.setStatus(PrintJobStatus.PENDING);
        job.setLastError(null);
        job.setCompletedAt(null);
        job.setClaimedByDeviceId(null);
        job.setClaimedAt(null);
        return toJobResponse(printJobRepository.save(job));
    }

    @Override
    @Transactional
    public PrintAgentDtos.JobResponse claimForReprint(PrintAgentDevice device, Long jobId) {
        PrintJob job = printJobRepository.findByIdAndBranchId(jobId, device.getBranchId())
                .orElseThrow(() -> new NotFoundException("Print job bulunamadi"));
        if (job.getStatus() == PrintJobStatus.FAILED || job.getStatus() == PrintJobStatus.DONE) {
            requeue(job);
            job = printJobRepository.findById(jobId).orElse(job);
        }
        if (job.getStatus() == PrintJobStatus.CLAIMED
                && device.getId().equals(job.getClaimedByDeviceId())) {
            return toJobResponse(job);
        }
        if (job.getStatus() != PrintJobStatus.PENDING) {
            throw new BadRequestException("Job tekrar yazdirma icin uygun degil");
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = printJobRepository.claimJob(job.getId(), device.getId(), now);
        if (updated != 1) {
            throw new BadRequestException("Job claim edilemedi");
        }
        return toJobResponse(printJobRepository.findById(job.getId()).orElse(job));
    }

    private boolean isPrintEnabled(Long branchId) {
        return branchRepository.findById(branchId)
                .filter(branch -> !branch.isDeleted())
                .map(branch -> branch.isPrintKitchenEnabled())
                .orElse(false);
    }

    private Optional<Long> resolvePrintBranch(Long ownerUserId) {
        return branchRepository.findByUserIdAndDeletedFalseOrderByIdDesc(ownerUserId).stream()
                .filter(branch -> branch.isPrintKitchenEnabled())
                .map(branch -> branch.getId())
                .findFirst();
    }

    private PrintAgentDtos.JobResponse toJobResponse(PrintJob job) {
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

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
