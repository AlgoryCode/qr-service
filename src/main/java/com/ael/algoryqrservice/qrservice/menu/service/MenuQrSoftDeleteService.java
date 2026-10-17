package com.ael.algoryqrservice.qrservice.menu.service;

import com.ael.algoryqrservice.qrservice.product.catalog.CatalogProducts;
import com.ael.algoryqrservice.qrservice.client.FulfillmentServiceClient;
import com.ael.algoryqrservice.qrservice.model.Menu;
import com.ael.algoryqrservice.qrservice.qr.model.Qr;
import com.ael.algoryqrservice.qrservice.repository.MenuRepository;
import com.ael.algoryqrservice.qrservice.qr.repository.QrRepository;
import com.ael.algoryqrservice.qrservice.product.service.menuindex.MenuProductIndexNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MenuQrSoftDeleteService {

    private final QrRepository qrRepository;
    private final MenuRepository menuRepository;
    private final FulfillmentServiceClient fulfillmentClient;
    private final MenuProductIndexNotifier menuProductIndexNotifier;

    @Transactional
    public void softDeleteMenuQr(Qr qr) {
        if (qr.isDeleted()) {
            return;
        }

        qr.setDeleted(true);
        qrRepository.save(qr);

        menuRepository.findByQrIdAndDeletedFalse(qr.getQrId()).ifPresent(menu -> {
            Long branchId = menu.getBranchId();
            softDeleteMenu(menu);
            if (branchId != null) {
                long remaining = menuRepository.countActiveLiveMenusForBranch(branchId);
                if (remaining >= 1) {
                    fulfillmentClient.release(qr.getUserId(), CatalogProducts.QR_MENU, 1);
                }
            }
        });
    }

    private void softDeleteMenu(Menu menu) {
        if (!menu.isDeleted()) {
            menu.setDeleted(true);
            menuRepository.save(menu);
            menuProductIndexNotifier.menuRemoved(menu.getMenuId());
        }
    }
}
