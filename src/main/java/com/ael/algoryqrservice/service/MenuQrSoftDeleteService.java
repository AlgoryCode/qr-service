package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.Qr;
import com.ael.algoryqrservice.model.enums.FulfillmentReferenceType;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.QrRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MenuQrSoftDeleteService {

    private final QrRepository qrRepository;
    private final MenuRepository menuRepository;
    private final EntitlementService entitlementService;
    private final FulfillmentGateService fulfillmentGateService;

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
                    fulfillmentGateService.releaseAddon(
                            qr.getUserId(),
                            CatalogProducts.QR_MENU,
                            1,
                            FulfillmentReferenceType.MENU,
                            qr.getQrId()
                    );
                }
            }
        });
        entitlementService.syncMenuEntitlements(qr.getUserId());
        entitlementService.syncQrCreateEntitlements(qr.getUserId());
    }

    private void softDeleteMenu(Menu menu) {
        if (!menu.isDeleted()) {
            menu.setDeleted(true);
            menuRepository.save(menu);
        }
    }
}
