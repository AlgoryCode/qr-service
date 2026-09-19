package com.ael.algoryqrservice.store.service;

import com.ael.algoryqrservice.store.model.Merchant;
import com.ael.algoryqrservice.store.model.StoreWorkingHour;
import com.ael.algoryqrservice.store.model.dto.StoreDtos;
import com.ael.algoryqrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MerchantMapper {

    private final StoreUrlBuilder storeUrlBuilder;
    private final StoreOpeningHours storeOpeningHours;

    public StoreDtos.MerchantResponse toResponse(Merchant merchant) {
        return StoreDtos.MerchantResponse.builder()
                .id(merchant.getId())
                .storeNo(merchant.getStoreNo())
                .slug(merchant.getSlug())
                .handle(storeUrlBuilder.buildHandle(merchant))
                .storeUrl(storeUrlBuilder.buildUrl(merchant))
                .status(merchant.getStatus())
                .open(storeOpeningHours.isOpenAt(merchant, AppTime.nowLocal()))
                .branchId(merchant.getBranchId())
                .catalogMenuId(merchant.getCatalogMenuId())
                .business(toBusinessInfo(merchant))
                .delivery(toDeliverySettings(merchant))
                .manuallyClosed(merchant.isManuallyClosed())
                .currency(merchant.getCurrency())
                .createdAt(merchant.getCreatedAt())
                .build();
    }

    public StoreDtos.BusinessInfo toBusinessInfo(Merchant merchant) {
        return StoreDtos.BusinessInfo.builder()
                .businessName(merchant.getBusinessName())
                .legalName(merchant.getLegalName())
                .taxOffice(merchant.getTaxOffice())
                .taxNumber(merchant.getTaxNumber())
                .phone(merchant.getPhone())
                .email(merchant.getEmail())
                .logoUrl(merchant.getLogoUrl())
                .coverUrl(merchant.getCoverUrl())
                .address(merchant.getAddress())
                .city(merchant.getCity())
                .district(merchant.getDistrict())
                .latitude(merchant.getLatitude())
                .longitude(merchant.getLongitude())
                .build();
    }

    public StoreDtos.DeliverySettings toDeliverySettings(Merchant merchant) {
        return StoreDtos.DeliverySettings.builder()
                .minOrderAmount(merchant.getMinOrderAmount())
                .deliveryFee(merchant.getDeliveryFee())
                .freeDeliveryThreshold(merchant.getFreeDeliveryThreshold())
                .avgPrepMinutes(merchant.getAvgPrepMinutes())
                .deliveryRadiusKm(merchant.getDeliveryRadiusKm())
                .deliveryTypes(merchant.getDeliveryTypes())
                .paymentMethods(merchant.getPaymentMethods())
                .workingHours(toWorkingHourDtos(merchant.getWorkingHours()))
                .build();
    }

    public void applyBusinessInfo(Merchant merchant, StoreDtos.BusinessInfo business) {
        merchant.setBusinessName(business.businessName().trim());
        merchant.setLegalName(trimToNull(business.legalName()));
        merchant.setTaxOffice(trimToNull(business.taxOffice()));
        merchant.setTaxNumber(trimToNull(business.taxNumber()));
        merchant.setPhone(trimToNull(business.phone()));
        merchant.setEmail(trimToNull(business.email()));
        merchant.setLogoUrl(trimToNull(business.logoUrl()));
        merchant.setCoverUrl(trimToNull(business.coverUrl()));
        merchant.setAddress(trimToNull(business.address()));
        merchant.setCity(trimToNull(business.city()));
        merchant.setDistrict(trimToNull(business.district()));
        merchant.setLatitude(business.latitude());
        merchant.setLongitude(business.longitude());
    }

    public void applyDeliverySettings(Merchant merchant, StoreDtos.DeliverySettings delivery) {
        merchant.setMinOrderAmount(delivery.minOrderAmount());
        merchant.setDeliveryFee(delivery.deliveryFee());
        merchant.setFreeDeliveryThreshold(delivery.freeDeliveryThreshold());
        merchant.setAvgPrepMinutes(delivery.avgPrepMinutes());
        merchant.setDeliveryRadiusKm(delivery.deliveryRadiusKm());
        merchant.setDeliveryTypes(new LinkedHashSet<>(delivery.deliveryTypes()));
        merchant.setPaymentMethods(new LinkedHashSet<>(delivery.paymentMethods()));
        merchant.setWorkingHours(toWorkingHourEntities(delivery.workingHours()));
    }

    private List<StoreDtos.WorkingHour> toWorkingHourDtos(List<StoreWorkingHour> hours) {
        if (hours == null) {
            return List.of();
        }
        return hours.stream()
                .map(hour -> StoreDtos.WorkingHour.builder()
                        .day(hour.getDay())
                        .opensAt(hour.getOpensAt())
                        .closesAt(hour.getClosesAt())
                        .closed(hour.isClosed())
                        .build())
                .toList();
    }

    private List<StoreWorkingHour> toWorkingHourEntities(List<StoreDtos.WorkingHour> hours) {
        if (hours == null) {
            return new ArrayList<>();
        }
        return hours.stream()
                .map(hour -> StoreWorkingHour.builder()
                        .day(hour.day())
                        .opensAt(hour.opensAt())
                        .closesAt(hour.closesAt())
                        .closed(hour.closed())
                        .build())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
