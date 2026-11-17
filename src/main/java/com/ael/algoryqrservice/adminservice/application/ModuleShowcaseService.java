package com.ael.algoryqrservice.adminservice.application;

import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.model.ModuleShowcase;
import com.ael.algoryqrservice.qrservice.model.Product;
import com.ael.algoryqrservice.qrservice.repository.ModuleShowcaseRepository;
import com.ael.algoryqrservice.qrservice.product.repository.ProductRepository;
import com.ael.algoryqrservice.qrservice.util.AppTime;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** How add-on modules look in the member app's module store. */
@Service
@RequiredArgsConstructor
public class ModuleShowcaseService {

    private final ModuleShowcaseRepository repository;
    private final ProductRepository productRepository;

    public record Request(
            @Size(max = 160) String tagline,
            ModuleShowcase.Badge badge,
            boolean featured,
            boolean hidden,
            @Min(0) @Max(9999) int sortOrder
    ) {
    }

    public record View(Long productId, String code, String name, String description, BigDecimal unitPrice,
                       boolean active, String tagline, ModuleShowcase.Badge badge, boolean featured, boolean hidden,
                       int sortOrder) {
    }

    @Transactional(readOnly = true)
    public List<View> list() {
        Map<Long, ModuleShowcase> showcase = repository.findAll().stream()
                .collect(Collectors.toMap(ModuleShowcase::getProductId, Function.identity()));
        return productRepository.findAll().stream()
                .filter(Product::isAddonPurchasable)
                .map(p -> view(p, showcase.get(p.getId())))
                .sorted(ORDER)
                .toList();
    }

    @Transactional
    public View save(Long productId, Request request, String actor) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BadRequestException("Ürün bulunamadı"));
        if (!product.isAddonPurchasable()) {
            throw new BadRequestException("Bu ürün ayrı satılan bir modül değil");
        }
        String tagline = request.tagline() == null || request.tagline().isBlank() ? null : request.tagline().trim();
        ModuleShowcase saved = repository.save(ModuleShowcase.builder()
                .productId(productId)
                .tagline(tagline)
                .badge(request.badge())
                .featured(request.featured())
                .hidden(request.hidden())
                .sortOrder(request.sortOrder())
                .updatedBy(actor)
                .updatedAt(AppTime.nowLocal())
                .build());
        return view(product, saved);
    }

    public static final Comparator<View> ORDER = Comparator
            .comparing(View::featured).reversed()
            .thenComparingInt(View::sortOrder)
            .thenComparing(View::name, Comparator.nullsLast(Comparator.naturalOrder()));

    private static View view(Product p, ModuleShowcase s) {
        return new View(p.getId(), p.getCode(), p.getName(), p.getDescription(), p.getUnitPrice(), p.isActive(),
                s == null ? null : s.getTagline(), s == null ? null : s.getBadge(), s != null && s.isFeatured(),
                s != null && s.isHidden(), s == null ? 0 : s.getSortOrder());
    }
}
