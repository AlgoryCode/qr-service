package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.dto.MenuDtos;
import com.ael.algoryqrservice.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/menu")
@RequiredArgsConstructor
public class MenuCoverController {

    private final MenuService menuService;

    @PostMapping("/{menuId}/cover")
    public ResponseEntity<MenuDtos.MenuProfileResponse> uploadCover(
            @PathVariable Long menuId,
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.status(201).body(menuService.uploadCover(menuId, file));
    }

    @DeleteMapping("/{menuId}/cover")
    public ResponseEntity<MenuDtos.MenuProfileResponse> deleteCover(@PathVariable Long menuId) {
        return ResponseEntity.ok(menuService.clearCover(menuId));
    }
}
