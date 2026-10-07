package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.client.FulfillmentServiceClient;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.Qr;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.QrRepository;
import com.ael.algoryqrservice.service.menuindex.MenuProductIndexNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MenuQrSoftDeleteService {

    private final QrRepository qrRepository;
    private final MenuRepository menuRepository;
    private final ObjectProvider<FulfillmentServiceClient> fulfillmentClients;
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
                    FulfillmentServiceClient client = fulfillmentClients.getIfAvailable();
                    if (client != null) {
                        client.release(qr.getUserId(), CatalogProducts.QR_MENU, 1);
                    }
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
