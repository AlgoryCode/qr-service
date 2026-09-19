package com.ael.algoryqrservice.store.model.dto;

import com.ael.algoryqrservice.store.model.MerchantStatus;
import com.ael.algoryqrservice.store.model.StoreCourierVehicleType;
import com.ael.algoryqrservice.store.model.StoreDeliveryType;
import com.ael.algoryqrservice.store.model.StorePaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

public final class StoreDtos {

    private StoreDtos() {
    }

    @Builder
    public record WorkingHour(
            @NotNull DayOfWeek day,
            LocalTime opensAt,
            LocalTime closesAt,
            boolean closed
    ) {
    }

    @Builder
    public record BusinessInfo(
            @NotBlank @Size(max = 255) String businessName,
            @Size(max = 255) String legalName,
            @Size(max = 120) String taxOffice,
            @Size(max = 32) String taxNumber,
            @Size(max = 32) String phone,
            @Size(max = 160) String email,
            @Size(max = 1024) String logoUrl,
            @Size(max = 1024) String coverUrl,
            String address,
            @Size(max = 80) String city,
            @Size(max = 80) String district,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
    }

    @Builder
    public record DeliverySettings(
            @NotNull @DecimalMin("0.0") BigDecimal minOrderAmount,
            @NotNull @DecimalMin("0.0") BigDecimal deliveryFee,
            @DecimalMin("0.0") BigDecimal freeDeliveryThreshold,
            @Min(0) @Max(600) int avgPrepMinutes,
            @DecimalMin("0.0") BigDecimal deliveryRadiusKm,
            @NotEmpty Set<StoreDeliveryType> deliveryTypes,
            @NotEmpty Set<StorePaymentMethod> paymentMethods,
            List<@Valid WorkingHour> workingHours
    ) {
    }

    public record SetupRequest(
            @NotNull @Valid BusinessInfo business,
            @NotNull @Valid DeliverySettings delivery,
            Long branchId,
            Long sourceMenuId
    ) {
    }

    @Builder
    public record BranchOption(Long id, String name, String address, String phone, String email, String photoUrl) {
    }

    @Builder
    public record MenuOption(Long menuId, String businessName, int productCount, String branchName) {
    }

    @Builder
    public record SetupPrefillResponse(
            boolean alreadySetUp,
            BusinessInfo suggested,
            List<BranchOption> branches,
            List<MenuOption> menus
    ) {
    }

    @Builder
    public record MerchantResponse(
            Long id,
            Long storeNo,
            String slug,
            String handle,
            String storeUrl,
            MerchantStatus status,
            boolean open,
            Long branchId,
            Long catalogMenuId,
            BusinessInfo business,
            DeliverySettings delivery,
            boolean manuallyClosed,
            String currency,
            LocalDateTime createdAt
    ) {
    }

    public record UpdateMerchantRequest(
            @Valid BusinessInfo business,
            @Valid DeliverySettings delivery,
            Boolean manuallyClosed,
            MerchantStatus status
    ) {
    }

    public record CourierRequest(
            @NotBlank @Size(max = 160) String fullName,
            @NotBlank @Size(max = 32) String phone,
            @NotNull StoreCourierVehicleType vehicleType,
            Boolean active
    ) {
    }

    @Builder
    public record CourierResponse(
            Long id,
            String fullName,
            String phone,
            StoreCourierVehicleType vehicleType,
            boolean active
    ) {
    }
}
