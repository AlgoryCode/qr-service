package com.ael.algoryqrservice.model.nutrition;

import com.ael.algoryqrservice.model.enums.NutritionBasis;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NutritionFacts {
    private NutritionBasis basis;
    private BigDecimal energyKj;
    private BigDecimal energyKcal;
    private BigDecimal fat;
    private BigDecimal saturatedFat;
    private BigDecimal carbohydrate;
    private BigDecimal sugars;
    private BigDecimal polyols;
    private BigDecimal starch;
    private BigDecimal fibre;
    private BigDecimal protein;
    private BigDecimal salt;
    @Builder.Default
    private List<NutritionNutrientEntry> vitaminsAndMinerals = new ArrayList<>();
    @Builder.Default
    private List<NutritionNutrientEntry> otherNutrients = new ArrayList<>();
}
