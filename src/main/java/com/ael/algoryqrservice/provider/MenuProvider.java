package com.ael.algoryqrservice.provider;

import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.Qr;
import com.ael.algoryqrservice.model.Type;
import com.ael.algoryqrservice.model.dto.QrRequest;
import com.ael.algoryqrservice.model.dto.QrResponse;
import com.ael.algoryqrservice.service.MenuService;
import com.ael.algoryqrservice.service.QrGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class MenuProvider implements QrProvider<QrRequest> {

    private final QrGenerationService qrGenerationService;
    private final MenuService menuService;

    @Override
    public Type getType() {
        return Type.MENU;
    }

    @Override
    public Class<QrRequest> requestType() {
        return QrRequest.class;
    }

    @Override
    public QrResponse createQr(QrRequest request) {
        try {
            Map<String, Object> details = request.getDetails();
            if (details == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "details zorunludur");
            }

            String initialContent = menuService.buildPublicUrlForQrId(0L);

            Qr qr = qrGenerationService.createAndSave(request, initialContent);
            Menu menu = menuService.createMenuForQr(qr, request);
            String publicUrl = menuService.buildPublicUrl(menu);
            qr = qrGenerationService.updateQrContent(qr, publicUrl);

            return QrResponse.builder()
                    .qrId(qr.getQrId())
                    .imgSrc(qr.getImgSrc())
                    .publicUrl(publicUrl)
                    .menuId(menu.getMenuId())
                    .build();
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate menu QR", e);
        }
    }
}
