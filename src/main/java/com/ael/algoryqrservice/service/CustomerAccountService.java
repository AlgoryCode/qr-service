package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.model.Customer;
import com.ael.algoryqrservice.model.CustomerMembership;
import com.ael.algoryqrservice.model.dto.CustomerAuthDtos;
import com.ael.algoryqrservice.model.enums.AuthProvider;
import com.ael.algoryqrservice.model.enums.MembershipStatus;
import com.ael.algoryqrservice.repository.CustomerMembershipRepository;
import com.ael.algoryqrservice.repository.CustomerRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CustomerAccountService {

    private final SecurityUtils securityUtils;
    private final CustomerRepository customerRepository;
    private final CustomerMembershipRepository membershipRepository;
    private final MenuRepository menuRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public CustomerAuthDtos.CustomerProfileResponse getMyProfile() {
        return toProfileResponse(securityUtils.getCurrentCustomer());
    }

    @Transactional
    public CustomerAuthDtos.CustomerProfileResponse updateMyProfile(
            CustomerAuthDtos.CustomerProfilePatchRequest request
    ) {
        Customer customer = securityUtils.getCurrentCustomer();

        if (request.getFirstName() != null) {
            String firstName = request.getFirstName().trim();
            if (firstName.isBlank()) {
                throw new BadRequestException("İsim boş olamaz");
            }
            customer.setFirstName(firstName);
        }

        if (request.getLastName() != null) {
            String lastName = request.getLastName().trim();
            customer.setLastName(lastName.isBlank() ? null : lastName);
        }

        if (request.getPhone() != null) {
            String phone = request.getPhone().trim();
            customer.setPhone(phone.isBlank() ? null : phone);
        }

        if (request.getAvatarKey() != null) {
            String avatarKey = request.getAvatarKey().trim();
            customer.setAvatarKey(avatarKey.isBlank() ? null : avatarKey);
        }

        return toProfileResponse(customerRepository.save(customer));
    }

    @Transactional
    public void changePassword(CustomerAuthDtos.CustomerChangePasswordRequest request) {
        Customer customer = securityUtils.getCurrentCustomer();
        if (customer.getProvider() != AuthProvider.BASIC) {
            throw new BadRequestException("Google hesabı için parola değiştirilemez");
        }

        if (customer.getPassword() == null
                || !passwordEncoder.matches(request.getCurrentPassword(), customer.getPassword())) {
            throw new BadRequestException("Mevcut şifre hatalı");
        }

        if (!request.getNewPassword().equals(request.getNewPasswordConfirm())) {
            throw new BadRequestException("Şifreler eşleşmiyor");
        }

        if (request.getCurrentPassword().equals(request.getNewPassword())) {
            throw new BadRequestException("Yeni şifre mevcut şifre ile aynı olamaz");
        }

        customer.setPassword(passwordEncoder.encode(request.getNewPassword()));
        customerRepository.save(customer);
    }

    @Transactional
    public CustomerAuthDtos.MembershipResponse joinMembership(Long menuId) {
        Long customerId = securityUtils.getCurrentCustomerId();
        return toMembershipResponse(upsertMembership(customerId, menuId));
    }

    @Transactional
    public CustomerAuthDtos.MembershipResponse joinMembership(Long customerId, Long menuId) {
        return toMembershipResponse(upsertMembership(customerId, menuId));
    }

    @Transactional(readOnly = true)
    public CustomerAuthDtos.MembershipResponse getMembership(Long menuId) {
        Long customerId = securityUtils.getCurrentCustomerId();
        CustomerMembership membership = membershipRepository.findByCustomerIdAndMenuId(customerId, menuId)
                .orElseThrow(() -> new NotFoundException("Üyelik bulunamadı"));
        return toMembershipResponse(membership);
    }

    private CustomerMembership upsertMembership(Long customerId, Long menuId) {
        if (menuId == null) {
            throw new BadRequestException("Menü zorunludur");
        }
        if (!menuRepository.existsById(menuId)) {
            throw new NotFoundException("Menü bulunamadı");
        }

        return membershipRepository.findByCustomerIdAndMenuId(customerId, menuId)
                .map(existing -> {
                    existing.setStatus(MembershipStatus.ACTIVE);
                    if (existing.getJoinedAt() == null) {
                        existing.setJoinedAt(LocalDateTime.now());
                    }
                    return membershipRepository.save(existing);
                })
                .orElseGet(() -> membershipRepository.save(CustomerMembership.builder()
                        .customerId(customerId)
                        .menuId(menuId)
                        .status(MembershipStatus.ACTIVE)
                        .joinedAt(LocalDateTime.now())
                        .build()));
    }

    CustomerAuthDtos.CustomerProfileResponse toProfileResponse(Customer customer) {
        return CustomerAuthDtos.CustomerProfileResponse.builder()
                .id(customer.getId())
                .firstName(customer.getFirstName())
                .lastName(customer.getLastName())
                .email(customer.getEmail())
                .phone(customer.getPhone())
                .provider(customer.getProvider())
                .avatarKey(customer.getAvatarKey())
                .createdAt(customer.getCreatedAt())
                .updatedAt(customer.getUpdatedAt())
                .build();
    }

    private CustomerAuthDtos.MembershipResponse toMembershipResponse(CustomerMembership membership) {
        return CustomerAuthDtos.MembershipResponse.builder()
                .id(membership.getId())
                .customerId(membership.getCustomerId())
                .menuId(membership.getMenuId())
                .status(membership.getStatus())
                .joinedAt(membership.getJoinedAt())
                .build();
    }
}
