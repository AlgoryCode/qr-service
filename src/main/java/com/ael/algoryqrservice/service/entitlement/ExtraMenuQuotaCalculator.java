package com.ael.algoryqrservice.service.entitlement;

import com.ael.algoryqrservice.repository.MenuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Every branch includes one live menu; anything beyond that is billed as an extra menu.
 * Single owner of that rule so quota display and usage sync can never drift apart.
 */
@Component
@RequiredArgsConstructor
public class ExtraMenuQuotaCalculator {

    private static final int FREE_MENUS_PER_BRANCH = 1;

    private final MenuRepository menuRepository;

    public int countExtraMenus(Long userId) {
        if (userId == null) {
            return 0;
        }
        List<Object[]> menusPerBranch = menuRepository.countActiveLiveMenusGroupedByBranch(userId);
        int extra = 0;
        for (Object[] row : menusPerBranch) {
            long liveMenus = ((Number) row[1]).longValue();
            extra += (int) Math.max(0, liveMenus - FREE_MENUS_PER_BRANCH);
        }
        return extra;
    }
}
