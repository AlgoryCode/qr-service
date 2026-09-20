package com.ael.algoryqrservice.print.service;

import com.ael.algoryqrservice.model.Branch;
import com.ael.algoryqrservice.print.dto.PrintAgentDtos;
import com.ael.algoryqrservice.print.model.PrintAgentDevice;
import com.ael.algoryqrservice.print.model.PrintJob;
import com.ael.algoryqrservice.print.model.PrintJobStatus;
import com.ael.algoryqrservice.print.model.PrintJobType;
import com.ael.algoryqrservice.print.model.PrintSourceType;
import com.ael.algoryqrservice.print.repository.PrintJobRepository;
import com.ael.algoryqrservice.repository.BranchRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrintJobServiceImplTest {

    @Mock
    private PrintJobRepository printJobRepository;

    @Mock
    private BranchRepository branchRepository;

    @InjectMocks
    private PrintJobServiceImpl printJobService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ObjectNode payload;

    @BeforeEach
    void setUp() {
        payload = objectMapper.createObjectNode();
        payload.put("id", 1);
    }

    @Test
    void enqueueKitchenTicket_whenPrintDisabled_thenSkip() {
        when(branchRepository.findById(4L)).thenReturn(Optional.of(Branch.builder()
                .id(4L)
                .userId(7L)
                .name("Sube")
                .printKitchenEnabled(false)
                .build()));

        Optional<PrintJob> result = printJobService.enqueueKitchenTicket(
                7L, 4L, PrintSourceType.MENU_ORDER, "10", payload);

        assertThat(result).isEmpty();
        verify(printJobRepository, never()).save(any());
    }

    @Test
    void enqueueKitchenTicket_whenIdempotent_thenReturnExisting() {
        when(branchRepository.findById(4L)).thenReturn(Optional.of(Branch.builder()
                .id(4L)
                .userId(7L)
                .name("Sube")
                .printKitchenEnabled(true)
                .build()));
        PrintJob existing = PrintJob.builder()
                .id(99L)
                .idempotencyKey("MENU_ORDER:10:KITCHEN_TICKET")
                .status(PrintJobStatus.PENDING)
                .build();
        when(printJobRepository.findByIdempotencyKey("MENU_ORDER:10:KITCHEN_TICKET"))
                .thenReturn(Optional.of(existing));

        Optional<PrintJob> result = printJobService.enqueueKitchenTicket(
                7L, 4L, PrintSourceType.MENU_ORDER, "10", payload);

        assertThat(result).contains(existing);
        verify(printJobRepository, never()).save(any());
    }

    @Test
    void enqueueKitchenTicket_whenEnabled_thenPersistPending() {
        when(branchRepository.findById(4L)).thenReturn(Optional.of(Branch.builder()
                .id(4L)
                .userId(7L)
                .name("Sube")
                .printKitchenEnabled(true)
                .deleted(false)
                .build()));
        when(printJobRepository.findByIdempotencyKey("MENU_ORDER:10:KITCHEN_TICKET"))
                .thenReturn(Optional.empty());
        when(printJobRepository.save(any(PrintJob.class))).thenAnswer(invocation -> {
            PrintJob job = invocation.getArgument(0);
            job.setId(55L);
            return job;
        });

        Optional<PrintJob> result = printJobService.enqueueKitchenTicket(
                7L, 4L, PrintSourceType.MENU_ORDER, "10", payload);

        assertThat(result).isPresent();
        ArgumentCaptor<PrintJob> captor = ArgumentCaptor.forClass(PrintJob.class);
        verify(printJobRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PrintJobStatus.PENDING);
        assertThat(captor.getValue().getJobType()).isEqualTo(PrintJobType.KITCHEN_TICKET);
        assertThat(captor.getValue().getBranchId()).isEqualTo(4L);
    }

    @Test
    void claimPending_whenJobsExist_thenClaimAtomically() {
        PrintAgentDevice device = PrintAgentDevice.builder().id(3L).branchId(4L).build();
        PrintJob pending = PrintJob.builder()
                .id(11L)
                .branchId(4L)
                .status(PrintJobStatus.PENDING)
                .sourceType(PrintSourceType.MENU_ORDER)
                .sourceId("10")
                .jobType(PrintJobType.KITCHEN_TICKET)
                .payloadJson(payload)
                .attempts(0)
                .build();
        when(printJobRepository.findPendingForBranch(4L)).thenReturn(List.of(pending));
        when(printJobRepository.claimJob(any(), any(), any())).thenReturn(1);
        when(printJobRepository.findById(11L)).thenReturn(Optional.of(pending));

        PrintAgentDtos.PendingJobsResponse response = printJobService.claimPending(device, 10);

        assertThat(response.getJobs()).hasSize(1);
        assertThat(response.getJobs().getFirst().getId()).isEqualTo(11L);
        verify(printJobRepository).releaseStaleClaims(any(), any());
    }
}
