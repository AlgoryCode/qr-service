package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.MenuProductOption;
import com.ael.algoryqrservice.model.MenuProductOptionGroup;
import com.ael.algoryqrservice.model.SelectedMenuOption;
import com.ael.algoryqrservice.model.dto.MenuDtos;
import com.ael.algoryqrservice.model.enums.MenuProductOptionGroupKind;
import com.ael.algoryqrservice.model.enums.MenuProductOptionUnit;
import com.ael.algoryqrservice.repository.MenuProductOptionGroupRepository;
import com.ael.algoryqrservice.util.Enums;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuProductOptionService {

    private final MenuProductOptionGroupRepository optionGroupRepository;

    @Transactional(readOnly = true)
    public List<MenuDtos.MenuProductOptionGroupResponse> load(Long productId) {
        return toResponses(optionGroupRepository.findByProductIdOrderBySortOrderAscIdAsc(productId));
    }

    @Transactional(readOnly = true)
    public Map<Long, List<MenuDtos.MenuProductOptionGroupResponse>> loadByProductIds(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        List<MenuProductOptionGroup> rows =
                optionGroupRepository.findByProductIdInOrderBySortOrderAscIdAsc(productIds);
        Map<Long, List<MenuProductOptionGroup>> grouped = new LinkedHashMap<>();
        for (MenuProductOptionGroup row : rows) {
            grouped.computeIfAbsent(row.getProductId(), ignored -> new ArrayList<>()).add(row);
        }
        Map<Long, List<MenuDtos.MenuProductOptionGroupResponse>> result = new LinkedHashMap<>();
        for (Long productId : productIds) {
            result.put(productId, toResponses(grouped.getOrDefault(productId, List.of())));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Map<Long, List<MenuProductOptionGroup>> loadEntitiesByProductIds(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        List<MenuProductOptionGroup> rows =
                optionGroupRepository.findByProductIdInOrderBySortOrderAscIdAsc(productIds);
        Map<Long, List<MenuProductOptionGroup>> grouped = new LinkedHashMap<>();
        for (MenuProductOptionGroup row : rows) {
            grouped.computeIfAbsent(row.getProductId(), ignored -> new ArrayList<>()).add(row);
        }
        for (Long productId : productIds) {
            grouped.putIfAbsent(productId, List.of());
        }
        return grouped;
    }

    @Transactional
    public void replace(Long productId, List<MenuDtos.MenuProductOptionGroupRequest> request) {
        optionGroupRepository.deleteByProductId(productId);
        if (request == null || request.isEmpty()) {
            return;
        }
        List<MenuProductOptionGroup> groups = new ArrayList<>();
        int groupSort = 0;
        for (MenuDtos.MenuProductOptionGroupRequest groupRequest : request) {
            if (groupRequest == null) {
                continue;
            }
            String groupName = trimRequired(groupRequest.getName(), "Opsiyon grubu adı zorunludur");
            MenuProductOptionGroupKind kind = resolveKind(groupRequest.getKind());
            MenuProductOptionUnit unit = resolveUnit(kind, groupRequest.getUnit());
            int minSelect = groupRequest.getMinSelect() == null ? 0 : groupRequest.getMinSelect();
            int maxSelect = groupRequest.getMaxSelect() == null ? 1 : groupRequest.getMaxSelect();
            if (minSelect < 0 || maxSelect < 1 || minSelect > maxSelect) {
                throw new BadRequestException("Opsiyon grubu seçim aralığı geçersiz: " + groupName);
            }
            MenuProductOptionGroup group = MenuProductOptionGroup.builder()
                    .productId(productId)
                    .name(groupName)
                    .kind(kind)
                    .unit(unit)
                    .minSelect(minSelect)
                    .maxSelect(maxSelect)
                    .sortOrder(groupRequest.getSortOrder() != null ? groupRequest.getSortOrder() : groupSort)
                    .options(new ArrayList<>())
                    .build();
            int optionSort = 0;
            List<MenuDtos.MenuProductOptionRequest> options =
                    groupRequest.getOptions() == null ? List.of() : groupRequest.getOptions();
            if (options.isEmpty()) {
                throw new BadRequestException("Opsiyon grubunda en az bir seçenek olmalı: " + groupName);
            }
            for (MenuDtos.MenuProductOptionRequest optionRequest : options) {
                if (optionRequest == null) {
                    continue;
                }
                String optionName = trimRequired(optionRequest.getName(), "Opsiyon adı zorunludur");
                BigDecimal delta = optionRequest.getPriceDelta() == null
                        ? BigDecimal.ZERO
                        : optionRequest.getPriceDelta();
                MenuProductOption option = MenuProductOption.builder()
                        .name(optionName)
                        .priceDelta(delta)
                        .available(optionRequest.getAvailable() == null || optionRequest.getAvailable())
                        .sortOrder(optionRequest.getSortOrder() != null ? optionRequest.getSortOrder() : optionSort)
                        .build();
                group.addOption(option);
                optionSort++;
            }
            if (group.getOptions().isEmpty()) {
                throw new BadRequestException("Opsiyon grubunda en az bir seçenek olmalı: " + groupName);
            }
            groups.add(group);
            groupSort++;
        }
        if (!groups.isEmpty()) {
            optionGroupRepository.saveAll(groups);
        }
    }

    @Transactional
    public void copyOptions(Map<Long, Long> sourceToTargetProductId) {
        if (sourceToTargetProductId == null || sourceToTargetProductId.isEmpty()) {
            return;
        }
        List<MenuProductOptionGroup> sourceGroups =
                optionGroupRepository.findByProductIdInOrderBySortOrderAscIdAsc(sourceToTargetProductId.keySet());
        if (sourceGroups.isEmpty()) {
            return;
        }
        List<MenuProductOptionGroup> copies = new ArrayList<>();
        for (MenuProductOptionGroup source : sourceGroups) {
            Long targetProductId = sourceToTargetProductId.get(source.getProductId());
            if (targetProductId == null) {
                continue;
            }
            MenuProductOptionGroup copy = MenuProductOptionGroup.builder()
                    .productId(targetProductId)
                    .name(source.getName())
                    .kind(source.getKind() == null ? MenuProductOptionGroupKind.CUSTOM : source.getKind())
                    .unit(source.getUnit() == null ? MenuProductOptionUnit.NONE : source.getUnit())
                    .minSelect(source.getMinSelect())
                    .maxSelect(source.getMaxSelect())
                    .sortOrder(source.getSortOrder())
                    .options(new ArrayList<>())
                    .build();
            for (MenuProductOption sourceOption : source.getOptions()) {
                copy.addOption(MenuProductOption.builder()
                        .name(sourceOption.getName())
                        .priceDelta(sourceOption.getPriceDelta())
                        .available(sourceOption.isAvailable())
                        .sortOrder(sourceOption.getSortOrder())
                        .build());
            }
            copies.add(copy);
        }
        if (!copies.isEmpty()) {
            optionGroupRepository.saveAll(copies);
        }
    }

    public ResolvedSelections resolveSelections(
            Long productId,
            List<MenuProductOptionGroup> groups,
            List<Long> selectedOptionIds
    ) {
        List<MenuProductOptionGroup> safeGroups = groups == null ? List.of() : groups;
        LinkedHashSet<Long> requestedIds = uniqueIds(selectedOptionIds);

        if (safeGroups.isEmpty()) {
            if (!requestedIds.isEmpty()) {
                throw new BadRequestException("Ürünün seçilebilir opsiyonu yok: " + productId);
            }
            return new ResolvedSelections(List.of(), BigDecimal.ZERO);
        }

        Map<Long, MenuProductOption> optionById = new LinkedHashMap<>();
        for (MenuProductOptionGroup group : safeGroups) {
            for (MenuProductOption option : group.getOptions()) {
                optionById.put(option.getId(), option);
            }
        }

        for (Long optionId : requestedIds) {
            MenuProductOption option = optionById.get(optionId);
            if (option == null) {
                throw new BadRequestException("Opsiyon bu ürüne ait değil: " + optionId);
            }
            if (!option.isAvailable()) {
                throw new BadRequestException("Opsiyon şu an seçilemez: " + option.getName());
            }
        }

        List<SelectedMenuOption> snapshots = new ArrayList<>();
        BigDecimal deltaTotal = BigDecimal.ZERO;
        for (MenuProductOptionGroup group : safeGroups) {
            List<MenuProductOption> chosen = group.getOptions().stream()
                    .filter(option -> requestedIds.contains(option.getId()))
                    .sorted(Comparator.comparing(MenuProductOption::getSortOrder)
                            .thenComparing(MenuProductOption::getId))
                    .toList();
            int count = chosen.size();
            if (count < group.getMinSelect()) {
                throw new BadRequestException(
                        "Zorunlu opsiyon seçilmedi: " + group.getName()
                                + " (en az " + group.getMinSelect() + ")");
            }
            if (count > group.getMaxSelect()) {
                throw new BadRequestException(
                        "Çok fazla opsiyon seçildi: " + group.getName()
                                + " (en fazla " + group.getMaxSelect() + ")");
            }
            for (MenuProductOption option : chosen) {
                BigDecimal delta = option.getPriceDelta() == null ? BigDecimal.ZERO : option.getPriceDelta();
                deltaTotal = deltaTotal.add(delta);
                snapshots.add(SelectedMenuOption.builder()
                        .groupId(group.getId())
                        .groupName(group.getName())
                        .optionId(option.getId())
                        .optionName(option.getName())
                        .priceDelta(delta)
                        .build());
            }
        }

        snapshots.sort(Comparator
                .comparing(SelectedMenuOption::getGroupId, Comparator.nullsLast(Long::compareTo))
                .thenComparing(SelectedMenuOption::getOptionId, Comparator.nullsLast(Long::compareTo)));
        return new ResolvedSelections(snapshots, deltaTotal);
    }

    public static String lineKey(Long productId, List<SelectedMenuOption> selectedOptions) {
        String optionPart = (selectedOptions == null ? List.<SelectedMenuOption>of() : selectedOptions).stream()
                .map(SelectedMenuOption::getOptionId)
                .filter(Objects::nonNull)
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        return productId + ":" + optionPart;
    }

    private static List<MenuDtos.MenuProductOptionGroupResponse> toResponses(List<MenuProductOptionGroup> groups) {
        return groups.stream()
                .map(group -> MenuDtos.MenuProductOptionGroupResponse.builder()
                        .groupId(group.getId())
                        .name(group.getName())
                        .kind((group.getKind() == null ? MenuProductOptionGroupKind.CUSTOM : group.getKind()).name())
                        .unit((group.getUnit() == null ? MenuProductOptionUnit.NONE : group.getUnit()).name())
                        .minSelect(group.getMinSelect())
                        .maxSelect(group.getMaxSelect())
                        .sortOrder(group.getSortOrder())
                        .options((group.getOptions() == null ? List.<MenuProductOption>of() : group.getOptions())
                                .stream()
                                .sorted(Comparator.comparing(MenuProductOption::getSortOrder)
                                        .thenComparing(MenuProductOption::getId))
                                .map(option -> MenuDtos.MenuProductOptionResponse.builder()
                                        .optionId(option.getId())
                                        .name(option.getName())
                                        .priceDelta(option.getPriceDelta() == null
                                                ? BigDecimal.ZERO
                                                : option.getPriceDelta())
                                        .available(option.isAvailable())
                                        .sortOrder(option.getSortOrder())
                                        .build())
                                .toList())
                        .build())
                .toList();
    }

    private static MenuProductOptionGroupKind resolveKind(String raw) {
        return Enums.parseOrDefault(MenuProductOptionGroupKind.class, raw, MenuProductOptionGroupKind.CUSTOM);
    }

    private static MenuProductOptionUnit resolveUnit(MenuProductOptionGroupKind kind, String raw) {
        MenuProductOptionUnit unit = Enums.parseOrDefault(MenuProductOptionUnit.class, raw, MenuProductOptionUnit.NONE);
        if (kind != MenuProductOptionGroupKind.PORTION) {
            return MenuProductOptionUnit.NONE;
        }
        return unit == MenuProductOptionUnit.NONE ? MenuProductOptionUnit.PIECE : unit;
    }

    private static LinkedHashSet<Long> uniqueIds(List<Long> values) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (values == null) {
            return ids;
        }
        for (Long value : values) {
            if (value != null && value > 0) {
                ids.add(value);
            }
        }
        return ids;
    }

    private static String trimRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(message);
        }
        return value.trim();
    }

    public record ResolvedSelections(List<SelectedMenuOption> selectedOptions, BigDecimal priceDeltaTotal) {
    }
}
