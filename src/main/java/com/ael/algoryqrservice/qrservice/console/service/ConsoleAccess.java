package com.ael.algoryqrservice.qrservice.console.service;

import com.ael.algoryqrservice.qrservice.branch.service.BranchService;
import com.ael.algoryqrservice.qrservice.model.Branch;
import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

/**
 * Who may touch which branch's devices. The owner reaches every branch of the merchant; a staff member (who comes
 * in through {@code /api/v1/staff/devices}) only their own branch. Anything else answers 404 like a missing row.
 */
@Component
@RequiredArgsConstructor
public class ConsoleAccess {

    private final BranchService branchService;
    private final SecurityUtils securityUtils;

    public Long merchantId() {
        return securityUtils.getCurrentMerchantId();
    }

    /** The calling staff member, or null for the owner. */
    public Long staffId() {
        return securityUtils.findCurrentStaffId().orElse(null);
    }

    /** Who is acting, for change logs: the staff id or the owner's name / e-mail. */
    public String actorName() {
        Long staffId = staffId();
        if (staffId != null) {
            return "Personel #" + staffId;
        }
        return securityUtils.findCurrentUser()
                .map(user -> {
                    String name = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                            + (user.getLastName() == null ? "" : user.getLastName())).trim();
                    return name.isEmpty() ? user.getEmail() : name;
                })
                .orElse("İşletme sahibi");
    }

    public Branch requireBranch(Long branchId) {
        Branch branch = branchService.requireOwned(branchId);
        checkBranch(branch.getId());
        return branch;
    }

    /** Rejects a staff member reaching outside their branch. */
    public void checkBranch(Long branchId) {
        if (staffId() != null && !Objects.equals(branchId, securityUtils.getCurrentWaiterBranchId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Şube bulunamadı");
        }
    }

    /** The staff member's branch; only for the staff endpoints. */
    public Long staffBranchId() {
        return securityUtils.getCurrentWaiterBranchId();
    }
}
