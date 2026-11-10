package com.ael.algoryqrservice.qrservice.order.service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Picks the single menu whose order list carries a marketplace connection's orders.
 *
 * <p>Marketplace orders belong to a platform connection, not to a menu. Listing them under every menu
 * made clients that merge several menus count each order once per menu. A connection now belongs to
 * the merchant's menu in its own branch; when no menu is in that branch, or the connection has no
 * branch, it falls back to the merchant's first menu so its orders never disappear. Active menus win
 * over inactive ones, because clients usually skip inactive menus.</p>
 */
public final class MarketplaceMenuOwner {

    public record MenuRef(Long menuId, Long branchId, boolean active) {
    }

    private static final Comparator<MenuRef> PREFERENCE = Comparator
            .comparing((MenuRef menu) -> !menu.active())
            .thenComparing(MenuRef::menuId, Comparator.nullsLast(Comparator.naturalOrder()));

    private MarketplaceMenuOwner() {
    }

    public static Long ownerMenuId(Long connectionBranchId, List<MenuRef> merchantMenus) {
        if (merchantMenus == null || merchantMenus.isEmpty()) {
            return null;
        }
        List<MenuRef> ordered = merchantMenus.stream().filter(Objects::nonNull).sorted(PREFERENCE).toList();
        if (connectionBranchId != null) {
            for (MenuRef menu : ordered) {
                if (connectionBranchId.equals(menu.branchId())) {
                    return menu.menuId();
                }
            }
        }
        return ordered.isEmpty() ? null : ordered.get(0).menuId();
    }
}
