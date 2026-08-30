package com.ael.algoryqrservice.controller.admin;

import com.ael.algoryqrservice.model.dto.TaxonomyDtos;
import com.ael.algoryqrservice.service.MenuTaxonomyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/taxonomy")
@RequiredArgsConstructor
public class AdminTaxonomyController {

    private final MenuTaxonomyService menuTaxonomyService;

    @PostMapping("/tags")
    @ResponseStatus(HttpStatus.CREATED)
    public TaxonomyDtos.TagResponse createTag(@RequestBody TaxonomyDtos.TagRequest request) {
        return menuTaxonomyService.createTag(request);
    }

    @PutMapping("/tags/{id}")
    public TaxonomyDtos.TagResponse updateTag(
            @PathVariable Long id,
            @RequestBody TaxonomyDtos.TagUpdateRequest request
    ) {
        return menuTaxonomyService.updateTag(id, request);
    }

    @DeleteMapping("/tags/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTag(@PathVariable Long id) {
        menuTaxonomyService.deleteTag(id);
    }

    @PostMapping("/allergens")
    @ResponseStatus(HttpStatus.CREATED)
    public TaxonomyDtos.AllergenResponse createAllergen(@RequestBody TaxonomyDtos.AllergenRequest request) {
        return menuTaxonomyService.createAllergen(request);
    }

    @PutMapping("/allergens/{id}")
    public TaxonomyDtos.AllergenResponse updateAllergen(
            @PathVariable Long id,
            @RequestBody TaxonomyDtos.AllergenUpdateRequest request
    ) {
        return menuTaxonomyService.updateAllergen(id, request);
    }

    @DeleteMapping("/allergens/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAllergen(@PathVariable Long id) {
        menuTaxonomyService.deleteAllergen(id);
    }
}
