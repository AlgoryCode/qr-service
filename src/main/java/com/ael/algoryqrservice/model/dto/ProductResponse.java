package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.ProductCode;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ProductResponse {

    private Long id;
    private ProductCode code;
    private String name;
    private String description;
    private boolean active;
    private LocalDateTime createdAt;
}
