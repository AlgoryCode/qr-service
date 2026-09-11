package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.dto.AdminUserDtos;
import com.ael.algoryqrservice.purchase.lifecycle.UserPackageLifecycleUseCases;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AdminUserPackageService {

    private final UserPackageLifecycleUseCases userPackageLifecycleUseCases;

    public AdminUserDtos.PackageLifecycleResponse updatePackage(
            Long userId,
            AdminUserDtos.PackageUpdateRequest request
    ) {
        if (request.getStatus() == null || request.getStatus().isBlank()) {
            if (request.getDays() == null) {
                throw new BadRequestException("days veya status gerekli");
            }
            return extend(userId, request.getDays());
        }
        return switch (request.getStatus().toUpperCase(Locale.ROOT)) {
            case "INACTIVE" -> deactivate(userId);
            case "ACTIVE" -> {
                if (request.getDays() == null) {
                    throw new BadRequestException("days gerekli");
                }
                yield reactivate(userId, request.getDays());
            }
            default -> throw new BadRequestException("status ACTIVE veya INACTIVE olmalı");
        };
    }

    public AdminUserDtos.PackageLifecycleResponse deactivate(Long userId) {
        return userPackageLifecycleUseCases.deactivate(userId);
    }

    public AdminUserDtos.PackageLifecycleResponse reactivate(Long userId, int days) {
        return userPackageLifecycleUseCases.reactivate(userId, days);
    }

    public AdminUserDtos.PackageLifecycleResponse extend(Long userId, int days) {
        return userPackageLifecycleUseCases.extend(userId, days);
    }
}
