package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.dto.AdminUserDtos;
import com.ael.algoryqrservice.purchase.lifecycle.UserPackageLifecycleUseCases;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminUserPackageService {

    private final UserPackageLifecycleUseCases userPackageLifecycleUseCases;

    public AdminUserDtos.PackageLifecycleResponse deactivate(Long userId) {
        return userPackageLifecycleUseCases.deactivate(userId);
    }

    public AdminUserDtos.PackageLifecycleResponse reactivate(Long userId, int days) {
        return userPackageLifecycleUseCases.reactivate(userId, days);
    }
}
