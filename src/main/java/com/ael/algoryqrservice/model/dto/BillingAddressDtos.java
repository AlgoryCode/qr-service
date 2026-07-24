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
            @NotNull(message = "Fatura tipi zorunludur") BillingAddressType type,
            String name,
            String surname,
            String legalName,
            @Pattern(regexp = "\\d{11}", message = "TCKN 11 haneli olmalıdır") String tckn,
            @Pattern(regexp = "\\d{10}", message = "VKN 10 haneli olmalıdır") String vkn,
            String taxOffice,
            String mersis,
            @NotBlank(message = "Ülke zorunludur") String country,
            @NotBlank(message = "Şehir zorunludur") String city,
            @NotBlank(message = "İlçe zorunludur") String district,
            @NotBlank(message = "Adres zorunludur") String address,
            @NotBlank(message = "Posta kodu zorunludur") String postcode,
            @NotBlank(message = "E-posta zorunludur") @Email(message = "Geçerli bir e-posta girin") String email,
            @NotBlank(message = "Telefon zorunludur") String phone,
            boolean taxpayerInvoice,
            boolean defaultAddress
    ) {
        @AssertTrue(message = "Bireysel fatura için ad, soyad ve (vergi mükellefi ise) TCKN zorunludur")
        public boolean isIndividualValid() {
            if (type != BillingAddressType.INDIVIDUAL) {
                return true;
            }
            return present(name) && present(surname) && (!taxpayerInvoice || tckn != null);
        }

        @AssertTrue(message = "Kurumsal fatura için unvan, VKN ve vergi dairesi zorunludur")
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
