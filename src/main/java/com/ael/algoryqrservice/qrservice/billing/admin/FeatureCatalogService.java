package com.ael.algoryqrservice.qrservice.billing.admin;

import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.exception.NotFoundException;
import com.ael.algoryqrservice.qrservice.model.Feature;
import com.ael.algoryqrservice.qrservice.repository.FeatureRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/** The feature catalog: what packages and add-ons grant and how each one is measured. */
@Service
@RequiredArgsConstructor
public class FeatureCatalogService {

    public record View(String key, String name, Feature.Kind kind, String unit, Feature.ResetPeriod resetPeriod,
                       String parentKey, String description, Integer freeQuota, boolean unlimitedWithParent,
                       boolean countedLive) {
    }

    public record Request(
            @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$", message = "Anahtar büyük harf, rakam ve _ içermeli")
            String key,
            @NotBlank @Size(max = 128) String name,
            @NotNull Feature.Kind kind,
            @Size(max = 32) String unit,
            Feature.ResetPeriod resetPeriod,
            @Size(max = 64) String parentKey,
            @Size(max = 500) String description,
            @PositiveOrZero Integer freeQuota,
            Boolean unlimitedWithParent
    ) {
    }

    /** Features whose usage is the live count of a resource; their kind is fixed to LIMIT. */
    static final List<String> COUNTED = List.of("QR_BRANCH", "QR_MENU", "MENU_PRODUCT", "QR_CREATE");

    private final FeatureRepository features;

    @Transactional(readOnly = true)
    public List<View> list() {
        return features.findAllByOrderByKindAscNameAsc().stream().map(FeatureCatalogService::toView).toList();
    }

    @Transactional
    public View save(Request request) {
        String key = request.key().trim().toUpperCase(Locale.ROOT);
        Feature feature = features.findById(key).orElseGet(() -> Feature.builder().key(key).build());
        if (COUNTED.contains(key) && request.kind() != Feature.Kind.LIMIT) {
            throw new BadRequestException("FEATURE_KIND_FIXED", "Bu özellik kayıt sayısıyla ölçülür; türü LIMIT kalmalı");
        }
        Feature.ResetPeriod reset = request.resetPeriod() == null ? Feature.ResetPeriod.NONE : request.resetPeriod();
        if (request.kind() == Feature.Kind.METERED && reset == Feature.ResetPeriod.NONE) {
            throw new BadRequestException("FEATURE_RESET_REQUIRED", "Dönemlik kotanın sıfırlanma periyodu olmalı");
        }
        if (request.kind() != Feature.Kind.METERED && reset != Feature.ResetPeriod.NONE) {
            throw new BadRequestException("FEATURE_RESET_ONLY_METERED", "Sıfırlanma yalnızca dönemlik kotalarda olur");
        }
        String parent = blankToNull(request.parentKey());
        if (parent != null) {
            parent = parent.toUpperCase(Locale.ROOT);
            if (parent.equals(key) || features.findById(parent).isEmpty()) {
                throw new BadRequestException("FEATURE_PARENT_UNKNOWN", "Üst özellik bulunamadı: " + parent);
            }
        }
        feature.setName(request.name().trim());
        feature.setKind(request.kind());
        feature.setUnit(blankToNull(request.unit()));
        feature.setResetPeriod(reset);
        feature.setParentKey(parent);
        feature.setDescription(blankToNull(request.description()));
        boolean metered = request.kind() == Feature.Kind.METERED;
        feature.setFreeQuota(metered ? request.freeQuota() : null);
        feature.setUnlimitedWithParent(metered && parent != null && Boolean.TRUE.equals(request.unlimitedWithParent()));
        return toView(features.save(feature));
    }

    @Transactional(readOnly = true)
    public Feature require(String key) {
        return features.findById(key).orElseThrow(() -> new NotFoundException("Özellik bulunamadı: " + key));
    }

    private static View toView(Feature feature) {
        return new View(feature.getKey(), feature.getName(), feature.getKind(), feature.getUnit(),
                feature.getResetPeriod(), feature.getParentKey(), feature.getDescription(),
                feature.getFreeQuota(), feature.isUnlimitedWithParent(), COUNTED.contains(feature.getKey()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
