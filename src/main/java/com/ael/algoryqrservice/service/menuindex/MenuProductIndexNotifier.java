package com.ael.algoryqrservice.service.menuindex;

import com.ael.algoryqrservice.model.MenuProduct;

import java.util.Collection;

/**
 * Announces menu product changes to the search index. Callers stay unaware of the
 * transport; the implementation defers delivery until the surrounding transaction commits.
 */
public interface MenuProductIndexNotifier {

    void productChanged(MenuProduct product);

    void productsChanged(Collection<MenuProduct> products);

    void productRemoved(Long menuId, Long productId);

    void menuRemoved(Long menuId);
}
