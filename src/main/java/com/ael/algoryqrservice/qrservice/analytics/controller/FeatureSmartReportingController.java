package com.ael.algoryqrservice.qrservice.analytics.controller;

import com.ael.algoryqrservice.qrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.qrservice.analytics.dto.SmartReportDtos;
import com.ael.algoryqrservice.qrservice.security.RequiresProductScope;
import com.ael.algoryqrservice.qrservice.analytics.service.SmartReportService;
import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/features/SMART_REPORTING")
@RequiredArgsConstructor
public class FeatureSmartReportingController {

    private final SmartReportService smartReportService;
    private final SecurityUtils securityUtils;

    @PostMapping("/branches/{branchId}/reports")
    @RequiresProductScope(CatalogScopes.SMART_REPORTING_OWNER)
    public ResponseEntity<SmartReportDtos.SmartReportAccepted> createBranchSmartReport(
            @PathVariable Long branchId,
            @Valid @RequestBody SmartReportDtos.SmartReportCreateRequest body
    ) {
        Long ownerId = securityUtils.getCurrentMerchantId();
        SmartReportDtos.SmartReportAccepted accepted = smartReportService.enqueueForBranch(
                branchId,
                ownerId,
                body.from(),
                body.to(),
                body.locale(),
                body.options()
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(accepted);
    }

    @PostMapping("/menus/{menuId}/reports")
    @RequiresProductScope(CatalogScopes.SMART_REPORTING_OWNER)
    public ResponseEntity<SmartReportDtos.SmartReportAccepted> createSmartReport(
            @PathVariable Long menuId,
            @Valid @RequestBody SmartReportDtos.SmartReportCreateRequest body
    ) {
        Long ownerId = securityUtils.getCurrentMerchantId();
        SmartReportDtos.SmartReportAccepted accepted = smartReportService.enqueue(
                menuId,
                ownerId,
                body.from(),
                body.to(),
                body.locale(),
                body.options()
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(accepted);
    }
}
