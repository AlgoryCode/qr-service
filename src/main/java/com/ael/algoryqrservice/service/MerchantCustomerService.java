package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.model.Customer;
import com.ael.algoryqrservice.model.CustomerMembership;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.dto.MenuWaiterDtos;
import com.ael.algoryqrservice.model.enums.MembershipStatus;
import com.ael.algoryqrservice.repository.CustomerMembershipRepository;
import com.ael.algoryqrservice.repository.CustomerRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MerchantCustomerService {

    private final CustomerMembershipRepository customerMembershipRepository;
    private final CustomerRepository customerRepository;
    private final MenuRepository menuRepository;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public List<MenuWaiterDtos.CustomerListItem> listCustomers(Long menuId) {
        requireOwnedMenu(menuId);

        List<CustomerMembership> memberships = customerMembershipRepository
                .findByMenuIdAndStatusOrderByJoinedAtDesc(menuId, MembershipStatus.ACTIVE);

        if (memberships.isEmpty()) {
            return List.of();
        }

        List<Long> customerIds = memberships.stream()
                .map(CustomerMembership::getCustomerId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, Customer> customersById = customerRepository.findAllById(customerIds).stream()
                .collect(Collectors.toMap(Customer::getId, Function.identity()));

        List<MenuWaiterDtos.CustomerListItem> items = new ArrayList<>();
        for (CustomerMembership membership : memberships) {
            Customer customer = customersById.get(membership.getCustomerId());
            if (customer == null) {
                continue;
            }
            items.add(MenuWaiterDtos.CustomerListItem.builder()
                    .customerId(customer.getId())
                    .firstName(customer.getFirstName())
                    .lastName(customer.getLastName())
                    .email(customer.getEmail())
                    .joinedAt(membership.getJoinedAt())
                    .memberSince(customer.getCreatedAt())
                    .build());
        }
        return items;
    }

    private Menu requireOwnedMenu(Long menuId) {
        Menu menu = menuRepository.findById(menuId)
                .filter(m -> !m.isDeleted())
                .orElseThrow(() -> new NotFoundException("Menü bulunamadı"));
        Long currentUserId = securityUtils.getCurrentUserId();
        if (!currentUserId.equals(menu.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu menüye erişim yetkiniz yok");
        }
        return menu;
    }
}
