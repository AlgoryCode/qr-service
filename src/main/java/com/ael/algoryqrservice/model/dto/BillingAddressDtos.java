package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.BillingAddressType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDateTime;

public final class BillingAddressDtos {
    private BillingAddressDtos() {
    }

    public record Request(
            @NotNull BillingAddressType type,
            String name,
            String surname,
            String legalName,
            @Pattern(regexp = "\\d{11}") String tckn,
            @Pattern(regexp = "\\d{10}") String vkn,
            String taxOffice,
            String mersis,
            @NotBlank String country,
            @NotBlank String city,
            @NotBlank String district,
            @NotBlank String address,
            @NotBlank String postcode,
            @NotBlank @Email String email,
            @NotBlank String phone,
            boolean taxpayerInvoice,
            boolean defaultAddress
    ) {
        @AssertTrue(message = "Bireysel fatura bilgileri eksik veya geçersiz")
        public boolean isIndividualValid() {
            if (type != BillingAddressType.INDIVIDUAL) {
                return true;
            }
            return present(name) && present(surname) && (!taxpayerInvoice || tckn != null);
        }

        @AssertTrue(message = "Kurumsal fatura bilgileri eksik veya geçersiz")
        public boolean isCorporateValid() {
            if (type != BillingAddressType.CORPORATE) {
                return true;
            }
            return present(legalName) && vkn != null && present(taxOffice);
        }

        private boolean present(String value) {
            return value != null && !value.isBlank();
        }
    }

    public record Response(
            Long id,
            BillingAddressType type,
            String name,
            String surname,
            String legalName,
            String tckn,
            String vkn,
            String taxOffice,
            String mersis,
            String country,
            String city,
            String district,
            String address,
            String postcode,
            String email,
            String phone,
            boolean taxpayerInvoice,
            boolean defaultAddress,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }
}
