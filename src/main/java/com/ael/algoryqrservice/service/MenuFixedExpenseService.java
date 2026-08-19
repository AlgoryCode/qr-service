package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.MenuFixedExpense;
import com.ael.algoryqrservice.model.dto.MenuFixedExpenseDtos;
import com.ael.algoryqrservice.repository.MenuFixedExpenseRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MenuFixedExpenseService {

    private final MenuFixedExpenseRepository menuFixedExpenseRepository;
    private final MenuRepository menuRepository;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public List<MenuFixedExpenseDtos.Response> list(Long menuId) {
        requireOwnedMenu(menuId);
        return menuFixedExpenseRepository.findByMenuIdOrderByTitleAsc(menuId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public MenuFixedExpenseDtos.Response create(Long menuId, MenuFixedExpenseDtos.CreateRequest request) {
        requireOwnedMenu(menuId);
        if (request == null) {
            throw new BadRequestException("İstek gövdesi zorunludur");
        }
        String title = requireText(request.getTitle(), "Başlık zorunludur");
        BigDecimal dailyAmount = requirePositiveAmount(request.getDailyAmount());
        LocalDateTime now = LocalDateTime.now();

        MenuFixedExpense expense = MenuFixedExpense.builder()
                .menuId(menuId)
                .title(title)
                .dailyAmount(dailyAmount)
                .active(request.getActive() == null || request.getActive())
                .createdAt(now)
                .updatedAt(now)
                .build();

        return toResponse(menuFixedExpenseRepository.save(expense));
    }

    @Transactional
    public MenuFixedExpenseDtos.Response update(
            Long menuId,
            Long expenseId,
            MenuFixedExpenseDtos.UpdateRequest request
    ) {
        requireOwnedMenu(menuId);
        MenuFixedExpense expense = requireExpense(menuId, expenseId);
        if (request == null) {
            throw new BadRequestException("İstek gövdesi zorunludur");
        }
        if (request.getTitle() != null) {
            expense.setTitle(requireText(request.getTitle(), "Başlık zorunludur"));
        }
        if (request.getDailyAmount() != null) {
            expense.setDailyAmount(requirePositiveAmount(request.getDailyAmount()));
        }
        if (request.getActive() != null) {
            expense.setActive(request.getActive());
        }
        expense.setUpdatedAt(LocalDateTime.now());
        return toResponse(menuFixedExpenseRepository.save(expense));
    }

    @Transactional
    public void delete(Long menuId, Long expenseId) {
        requireOwnedMenu(menuId);
        MenuFixedExpense expense = requireExpense(menuId, expenseId);
        menuFixedExpenseRepository.delete(expense);
    }

    @Transactional(readOnly = true)
    public BigDecimal totalDailyActiveAmount(Long menuId) {
        return menuFixedExpenseRepository.findByMenuIdAndActiveTrueOrderByTitleAsc(menuId).stream()
                .map(MenuFixedExpense::getDailyAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private MenuFixedExpense requireExpense(Long menuId, Long expenseId) {
        return menuFixedExpenseRepository.findById(expenseId)
                .filter(expense -> menuId.equals(expense.getMenuId()))
                .orElseThrow(() -> new NotFoundException("Sabit gider bulunamadı"));
    }

    private void requireOwnedMenu(Long menuId) {
        Long userId = securityUtils.getCurrentUserId();
        Menu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new NotFoundException("Menü bulunamadı"));
        if (!userId.equals(menu.getUserId())) {
            throw new NotFoundException("Menü bulunamadı");
        }
    }

    private MenuFixedExpenseDtos.Response toResponse(MenuFixedExpense expense) {
        return MenuFixedExpenseDtos.Response.builder()
                .id(expense.getId())
                .menuId(expense.getMenuId())
                .title(expense.getTitle())
                .dailyAmount(expense.getDailyAmount())
                .active(expense.isActive())
                .createdAt(expense.getCreatedAt())
                .updatedAt(expense.getUpdatedAt())
                .build();
    }

    private BigDecimal requirePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(new BigDecimal("0.01")) < 0) {
            throw new BadRequestException("Tutar 0,01 veya daha büyük olmalıdır");
        }
        return amount;
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new BadRequestException(message);
        }
        return value.trim();
    }
}
