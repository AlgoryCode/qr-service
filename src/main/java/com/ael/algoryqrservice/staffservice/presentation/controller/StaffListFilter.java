package com.ael.algoryqrservice.staffservice.presentation.controller;

import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.model.enums.StaffRole;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Reads the comma-separated staff list filters; the single-value parameters still work. */
final class StaffListFilter {

    /** Value in {@code branches} that stands for staff without a branch. */
    static final String NO_BRANCH = "none";

    record Branches(List<Long> ids, boolean includeNoBranch) {
    }

    private StaffListFilter() {
    }

    static Set<StaffRole> parseRoles(String roles, StaffRole legacyRole) {
        Set<StaffRole> result = EnumSet.noneOf(StaffRole.class);
        if (legacyRole != null) {
            result.add(legacyRole);
        }
        if (roles == null || roles.isBlank()) {
            return result;
        }
        for (String part : roles.split(",")) {
            String value = part.trim();
            if (value.isEmpty()) continue;
            try {
                result.add(StaffRole.valueOf(value.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("Geçersiz personel rolü");
            }
        }
        return result;
    }

    static Branches parseBranches(String branches, Long legacyBranchId) {
        Set<Long> ids = new LinkedHashSet<>();
        boolean noBranch = false;
        if (legacyBranchId != null && legacyBranchId > 0) {
            ids.add(legacyBranchId);
        }
        if (branches != null && !branches.isBlank()) {
            for (String part : branches.split(",")) {
                String value = part.trim();
                if (value.isEmpty()) continue;
                if (NO_BRANCH.equalsIgnoreCase(value)) {
                    noBranch = true;
                    continue;
                }
                try {
                    long id = Long.parseLong(value);
                    if (id > 0) ids.add(id);
                } catch (NumberFormatException ex) {
                    throw new BadRequestException("Geçersiz şube");
                }
            }
        }
        return new Branches(new ArrayList<>(ids), noBranch);
    }
}
