package com.ael.algoryqrservice.qrservice.print.service;

import com.ael.algoryqrservice.qrservice.print.dto.PrintAgentDtos;
import com.ael.algoryqrservice.qrservice.print.model.PrintAgentDevice;
import com.ael.algoryqrservice.qrservice.print.model.PrintJob;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;
import com.ael.algoryqrservice.qrservice.print.model.PrintSourceType;

public interface PrintJobService {

    Optional<PrintJob> enqueueKitchenTicket(
            Long merchantId,
            Long branchId,
            com.ael.algoryqrservice.qrservice.print.model.PrintSourceType sourceType,
            String sourceId,
            JsonNode payload
    );

    PrintAgentDtos.PendingJobsResponse claimPending(PrintAgentDevice device, int limit);

    void ack(PrintAgentDevice device, Long jobId, PrintAgentDtos.AckRequest request);

    PrintAgentDtos.JobResponse requeue(PrintJob job);

    PrintAgentDtos.JobResponse claimForReprint(PrintAgentDevice device, Long jobId);
}
