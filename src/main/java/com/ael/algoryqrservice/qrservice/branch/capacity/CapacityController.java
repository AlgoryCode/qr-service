package com.ael.algoryqrservice.qrservice.branch.capacity;

import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The member's side of the over-limit flow: what is over, until when, and which branches to keep. */
@RestController
@RequestMapping("/api/v1/capacity/branches")
@RequiredArgsConstructor
public class CapacityController {

    public record KeepRequest(@NotNull @Size(max = 500) List<Long> branchIds) {
    }

    private final CapacityOverageService capacity;
    private final SecurityUtils securityUtils;

    @GetMapping
    public ResponseEntity<CapacityOverageService.Status> status() {
        return ResponseEntity.ok(capacity.status(securityUtils.getCurrentMerchantId()));
    }

    @PostMapping("/keep")
    public ResponseEntity<CapacityOverageService.Status> keep(@Valid @RequestBody KeepRequest request) {
        return ResponseEntity.ok(capacity.keep(securityUtils.getCurrentMerchantId(), request.branchIds()));
    }
}
