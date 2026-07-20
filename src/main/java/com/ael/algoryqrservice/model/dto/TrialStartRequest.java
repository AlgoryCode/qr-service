package com.ael.algoryqrservice.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TrialStartRequest {

    @NotNull(message = "Paket id zorunludur")
    private Long packageId;
}
