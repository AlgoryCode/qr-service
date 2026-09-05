package com.ael.algoryqrservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SelectedMenuOption {

    private Long groupId;
    private String groupName;
    private Long optionId;
    private String optionName;
    private BigDecimal priceDelta;
}
