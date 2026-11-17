package com.ael.algoryqrservice.qrservice.menu.service;

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
    private final MenuProductIndexNotifier menuProductIndexNotifier;

    @Transactional
    public void softDeleteMenuQr(Qr qr) {
        if (qr.isDeleted()) {
            return;
        }

        qr.setDeleted(true);
        qrRepository.save(qr);

        // Menu capacity is counted live, so deleting the menu frees its slot by itself.
        menuRepository.findByQrIdAndDeletedFalse(qr.getQrId()).ifPresent(this::softDeleteMenu);
    }

    private void softDeleteMenu(Menu menu) {
        if (!menu.isDeleted()) {
            menu.setDeleted(true);
            menuRepository.save(menu);
            menuProductIndexNotifier.menuRemoved(menu.getMenuId());
        }
    }
}
