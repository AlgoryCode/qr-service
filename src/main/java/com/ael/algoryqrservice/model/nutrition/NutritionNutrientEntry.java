package com.ael.algoryqrservice.model.nutrition;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NutritionNutrientEntry {
    private String name;
    private BigDecimal value;
    private String unit;
}
