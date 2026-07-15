package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.PackageCode;
import com.ael.algoryqrservice.model.enums.ProductCode;
import com.ael.algoryqrservice.model.enums.ProductScope;

import java.util.List;

public record UserAccessProfile(
        PackageCode activePackage,
        List<ProductCode> products,
        List<ProductScope> scopes
) {
}
