package com.ael.algoryqrservice.qrservice.order.controller;

import com.ael.algoryqrservice.qrservice.branch.dto.RestaurantTableDtos;
import com.ael.algoryqrservice.qrservice.model.Menu;
import com.ael.algoryqrservice.qrservice.menu.service.MenuService;
import com.ael.algoryqrservice.qrservice.order.service.TableSessionService;
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
        Menu menu = menuService.requireActivePublicMenu(publicId);
        return ResponseEntity.status(201).body(
                tableSessionService.openSession(menu, request.getTableToken())
        );
    }
}
