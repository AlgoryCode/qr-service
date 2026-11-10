package com.ael.algoryqrservice.qrservice.order.service;

import com.ael.algoryqrservice.qrservice.order.service.MarketplaceMenuOwner.MenuRef;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarketplaceMenuOwnerTest {

    private static final List<MenuRef> MENUS = List.of(
            new MenuRef(19L, null, true),
            new MenuRef(16L, 24L, true),
            new MenuRef(11L, 10L, false),
            new MenuRef(8L, 5L, true)
    );

    @Test
    void connectionBelongsToTheMenuInItsBranch() {
        assertThat(MarketplaceMenuOwner.ownerMenuId(24L, MENUS)).isEqualTo(16L);
        assertThat(MarketplaceMenuOwner.ownerMenuId(5L, MENUS)).isEqualTo(8L);
    }

    @Test
    void connectionWithoutAMatchingMenuFallsBackToTheFirstActiveMenu() {
        assertThat(MarketplaceMenuOwner.ownerMenuId(null, MENUS)).isEqualTo(8L);
        assertThat(MarketplaceMenuOwner.ownerMenuId(1L, MENUS)).isEqualTo(8L);
    }

    @Test
    void inactiveMenuStillOwnsItsBranchWhenItIsTheOnlyOneThere() {
        assertThat(MarketplaceMenuOwner.ownerMenuId(10L, MENUS)).isEqualTo(11L);
    }

    @Test
    void activeMenuWinsInsideTheSameBranch() {
        List<MenuRef> menus = List.of(new MenuRef(3L, 5L, false), new MenuRef(9L, 5L, true));
        assertThat(MarketplaceMenuOwner.ownerMenuId(5L, menus)).isEqualTo(9L);
    }

    @Test
    void merchantWithoutMenusHasNoOwner() {
        assertThat(MarketplaceMenuOwner.ownerMenuId(5L, List.of())).isNull();
    }
}
