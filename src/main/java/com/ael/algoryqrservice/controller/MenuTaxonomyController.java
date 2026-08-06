package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.dto.TaxonomyDtos;
import com.ael.algoryqrservice.service.MenuTaxonomyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/menu")
@RequiredArgsConstructor
public class MenuTaxonomyController {

    private final MenuTaxonomyService menuTaxonomyService;

    @GetMapping("/taxonomy")
    public ResponseEntity<List<TaxonomyDtos.MainCategoryResponse>> listTaxonomy() {
        return ResponseEntity.ok(menuTaxonomyService.listTaxonomy());
    }

    @GetMapping("/tags")
    public ResponseEntity<List<TaxonomyDtos.TagResponse>> listTags() {
        return ResponseEntity.ok(menuTaxonomyService.listTags());
    }

    @GetMapping("/allergens")
    public ResponseEntity<List<TaxonomyDtos.AllergenResponse>> listAllergens() {
        return ResponseEntity.ok(menuTaxonomyService.listAllergens());
    }
}
