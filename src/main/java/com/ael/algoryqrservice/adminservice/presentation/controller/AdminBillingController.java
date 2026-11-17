package com.ael.algoryqrservice.adminservice.presentation.controller;

import com.ael.algoryqrservice.adminservice.application.AdminBillingService;
import com.ael.algoryqrservice.adminservice.application.CollectionService;
import com.ael.algoryqrservice.adminservice.application.DashboardSecurityUtils;
import com.ael.algoryqrservice.adminservice.dto.AdminBillingDtos;
import com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity.DashboardUser;
import com.ael.algoryqrservice.qrservice.billing.collection.CollectionCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/billing")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminBillingController {

    private final AdminBillingService adminBillingService;
    private final CollectionService collectionService;
    private final DashboardSecurityUtils dashboardSecurityUtils;

    /**
     * Paying merchants by standing (PAID, DUE_SOON, OVERDUE, LAPSED) and collection follow-up
     * (OPEN, NONE or a collection status); omit either for all.
     */
    @GetMapping("/accounts")
    public ResponseEntity<AdminBillingDtos.AccountPage> listAccounts(
            @RequestParam(required = false) String standing,
            @RequestParam(required = false) String collection,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String paymentStyle,
            @RequestParam(required = false) Integer dueWithinDays,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(adminBillingService.list(
                standing, collection, q, paymentStyle, dueWithinDays, sort, page, size));
    }

    @PostMapping("/collections")
    public ResponseEntity<AdminBillingDtos.CollectionCaseResponse> openCollection(
            @Valid @RequestBody AdminBillingDtos.OpenCollectionRequest request
    ) {
        CollectionCase opened = collectionService.open(request.merchantId(), request.note(), actor());
        return ResponseEntity.ok(response(opened));
    }

    @GetMapping("/collections/{caseId}")
    public ResponseEntity<AdminBillingDtos.CollectionCaseResponse> getCollection(@PathVariable Long caseId) {
        return ResponseEntity.ok(response(collectionService.get(caseId)));
    }

    @PostMapping("/collections/{caseId}/events")
    public ResponseEntity<AdminBillingDtos.CollectionCaseResponse> recordCollectionEvent(
            @PathVariable Long caseId,
            @Valid @RequestBody AdminBillingDtos.CollectionEventRequest request
    ) {
        CollectionCase updated = collectionService.record(caseId, request.status(), request.note(), actor());
        return ResponseEntity.ok(response(updated));
    }

    private AdminBillingDtos.CollectionCaseResponse response(CollectionCase c) {
        return new AdminBillingDtos.CollectionCaseResponse(
                c.getId(), c.getMerchantId(), c.getPurchaseId(), c.getStatus(), c.getAmount(), c.getCurrency(),
                c.getDueSince(), c.getOpenedAt(), c.getStatusAt(), c.getClosedAt(),
                collectionService.history(c.getId()).stream()
                        .map(e -> new AdminBillingDtos.CollectionEventResponse(
                                e.getId(), e.getFromStatus(), e.getToStatus(), e.getNote(), e.getActor(), e.getCreatedAt()))
                        .toList());
    }

    private String actor() {
        DashboardUser user = dashboardSecurityUtils.getCurrentDashboardUser();
        return user.getUsername() != null && !user.getUsername().isBlank() ? user.getUsername() : user.getEmail();
    }
}
