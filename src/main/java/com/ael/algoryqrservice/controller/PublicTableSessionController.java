package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.dto.RestaurantTableDtos;
import com.ael.algoryqrservice.service.MenuService;
import com.ael.algoryqrservice.service.TableSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/menu/public")
@RequiredArgsConstructor
public class PublicTableSessionController {

    private final TableSessionService tableSessionService;
    private final MenuService menuService;

    @PostMapping("/{publicId}/table-session")
    public ResponseEntity<RestaurantTableDtos.TableSessionResponse> openTableSession(
            @PathVariable String publicId,
            @Valid @RequestBody RestaurantTableDtos.OpenTableSessionRequest request
    ) {
        Long qrId = menuService.requirePublicQrId(publicId);
        return ResponseEntity.status(201).body(
                tableSessionService.openSession(qrId, request.getTableToken())
        );
    }
}
