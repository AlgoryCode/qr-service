package com.ael.algoryqrservice.model.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ProductResponse {

    private Long id;
    private String code;
    private String name;
    private String description;
    private String scopeCode;
    private boolean consumable;
    private boolean active;
    private LocalDateTime createdAt;
}
