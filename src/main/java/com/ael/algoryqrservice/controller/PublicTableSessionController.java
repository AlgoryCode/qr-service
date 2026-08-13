package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.dto.RestaurantTableDtos;
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

    @PostMapping("/id/{qrId}/table-session")
    public ResponseEntity<RestaurantTableDtos.TableSessionResponse> openTableSession(
            @PathVariable Long qrId,
            @Valid @RequestBody RestaurantTableDtos.OpenTableSessionRequest request
    ) {
        return ResponseEntity.status(201).body(
                tableSessionService.openSession(qrId, request.getTableToken())
        );
    }
}
