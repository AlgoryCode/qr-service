package com.ael.algoryqrservice.qrservice.billing.admin;

import com.ael.algoryqrservice.qrservice.model.Feature;
import com.ael.algoryqrservice.qrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.qrservice.repository.FeatureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Turns a package's lines into the bullet list shown on pricing pages ("3 şube", "Akıllı rapor"). */
@Component
@RequiredArgsConstructor
public class FeatureLabels {

    private final FeatureRepository featureRepository;

    public List<String> describe(List<PlanPackageItem> items) {
        Map<String, Feature> features = featureRepository.findAll().stream()
                .collect(Collectors.toMap(Feature::getKey, Function.identity()));
        return items.stream()
                .filter(item -> item.getProduct() != null)
                .sorted(Comparator.comparing((PlanPackageItem item) -> kindOrder(features.get(item.getProduct().getFeatureCode())))
                        .thenComparing(item -> item.getProduct().getName()))
                .map(item -> label(item, features.get(item.getProduct().getFeatureCode())))
                .distinct()
                .toList();
    }

    private static String label(PlanPackageItem item, Feature feature) {
        String name = feature == null ? item.getProduct().getName() : feature.getName();
        if (feature == null || feature.getKind() == Feature.Kind.BOOLEAN) {
            return name;
        }
        String unit = feature.getUnit() == null || feature.getUnit().isBlank() ? name.toLowerCase() : feature.getUnit();
        String amount = item.isUnlimited() ? "Sınırsız" : String.valueOf(item.getQuantity());
        String period = switch (feature.getResetPeriod()) {
            case DAY -> " / gün";
            case WEEK -> " / hafta";
            case MONTH -> " / ay";
            case NONE -> "";
        };
        return amount + " " + unit + period;
    }

    private static int kindOrder(Feature feature) {
        if (feature == null) {
            return 9;
        }
        return switch (feature.getKind()) {
            case LIMIT -> 0;
            case METERED -> 1;
            case CREDIT -> 2;
            case BOOLEAN -> 3;
        };
    }
}
