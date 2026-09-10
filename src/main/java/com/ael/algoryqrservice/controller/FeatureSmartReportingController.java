package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.model.dto.SmartReportDtos;
import com.ael.algoryqrservice.security.RequiresProductScope;
import com.ael.algoryqrservice.service.SmartReportService;
import com.ael.algoryqrservice.util.SecurityUtils;
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
        Long ownerId = securityUtils.getCurrentUser().getId();
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
        Long ownerId = securityUtils.getCurrentUser().getId();
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
