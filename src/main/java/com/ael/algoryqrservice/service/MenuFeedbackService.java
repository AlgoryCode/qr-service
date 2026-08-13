package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.MenuProduct;
import com.ael.algoryqrservice.model.MenuProductRating;
import com.ael.algoryqrservice.model.MenuRating;
import com.ael.algoryqrservice.model.dto.AnalyticsDtos;
import com.ael.algoryqrservice.model.dto.MenuDtos;
import com.ael.algoryqrservice.repository.MenuProductRatingRepository;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import com.ael.algoryqrservice.repository.MenuRatingRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuFeedbackService {

    private static final int SAMPLE_COMMENTS = 20;
    private static final int RATED_PRODUCTS_LIMIT = 10;
    private static final long MIN_RATINGS_FOR_RANK = 1L;
    private static final int COMMENT_MAX_LEN = 280;

    private final MenuRepository menuRepository;
    private final MenuRatingRepository menuRatingRepository;
    private final MenuProductRatingRepository menuProductRatingRepository;
    private final MenuProductRepository menuProductRepository;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public MenuDtos.FeedbackPageResponse listFeedback(
            Long menuId,
            String type,
            LocalDate from,
            LocalDate to,
            Integer minScore,
            int page,
            int size
    ) {
        requireOwnedMenu(menuId);
        String normalizedType = normalizeType(type);
        LocalDateTime fromDt = from == null ? null : from.atStartOfDay();
        LocalDateTime toDt = to == null ? null : to.plusDays(1).atStartOfDay().minusNanos(1);
        Short min = minScore == null ? null : minScore.shortValue();
        if (min != null && (min < 1 || min > 5)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "minScore 1 ile 5 arasında olmalıdır");
        }

        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : Math.min(size, 100);

        if ("menu".equals(normalizedType)) {
            Pageable pageable = PageRequest.of(safePage, safeSize);
            Page<MenuRating> result = menuRatingRepository.findForOwner(menuId, fromDt, toDt, min, pageable);
            List<MenuDtos.FeedbackItemResponse> content = result.getContent().stream()
                    .map(this::toMenuItem)
                    .toList();
            return pageResponse(content, safePage, safeSize, result.getTotalElements());
        }

        if ("product".equals(normalizedType)) {
            Pageable pageable = PageRequest.of(safePage, safeSize);
            Page<MenuProductRating> result =
                    menuProductRatingRepository.findForOwner(menuId, fromDt, toDt, min, pageable);
            Map<Long, String> productNames = productNames(menuId);
            List<MenuDtos.FeedbackItemResponse> content = result.getContent().stream()
                    .map(r -> toProductItem(r, productNames))
                    .toList();
            return pageResponse(content, safePage, safeSize, result.getTotalElements());
        }

        // type=all: merge in memory (bounded page size)
        Pageable fetchAll = PageRequest.of(0, 500);
        List<MenuDtos.FeedbackItemResponse> merged = new ArrayList<>();
        menuRatingRepository.findForOwner(menuId, fromDt, toDt, min, fetchAll)
                .forEach(r -> merged.add(toMenuItem(r)));
        Map<Long, String> productNames = productNames(menuId);
        menuProductRatingRepository.findForOwner(menuId, fromDt, toDt, min, fetchAll)
                .forEach(r -> merged.add(toProductItem(r, productNames)));
        merged.sort(Comparator.comparing(MenuDtos.FeedbackItemResponse::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        long total = merged.size();
        int fromIndex = Math.min(safePage * safeSize, merged.size());
        int toIndex = Math.min(fromIndex + safeSize, merged.size());
        return pageResponse(merged.subList(fromIndex, toIndex), safePage, safeSize, total);
    }

    @Transactional(readOnly = true)
    public MenuDtos.FeedbackSummaryResponse getSummary(Long menuId) {
        requireOwnedMenu(menuId);
        return MenuDtos.FeedbackSummaryResponse.builder()
                .menuId(menuId)
                .menu(MenuDtos.FeedbackBucketSummary.builder()
                        .ratingAvg(toAvg(menuRatingRepository.averageScoreByMenuId(menuId)))
                        .ratingCount(menuRatingRepository.countByMenuId(menuId))
                        .scoreHistogram(toHistogram(menuRatingRepository.scoreHistogramByMenuId(menuId)))
                        .build())
                .products(MenuDtos.FeedbackBucketSummary.builder()
                        .ratingAvg(toAvg(menuProductRatingRepository.averageScoreByMenuId(menuId)))
                        .ratingCount(menuProductRatingRepository.countByMenuId(menuId))
                        .scoreHistogram(toHistogram(menuProductRatingRepository.scoreHistogramByMenuId(menuId)))
                        .build())
                .build();
    }

    @Transactional(readOnly = true)
    public AnalyticsDtos.ReportFeedback buildReportFeedback(Long menuId, LocalDate from, LocalDate to) {
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.plusDays(1).atStartOfDay().minusNanos(1);
        Map<Long, String> productNames = productNames(menuId);
        Pageable samplePage = PageRequest.of(0, SAMPLE_COMMENTS);
        Pageable topPage = PageRequest.of(0, RATED_PRODUCTS_LIMIT);

        List<AnalyticsDtos.FeedbackCommentSample> menuComments = menuRatingRepository
                .sampleCommentsByMenuIdAndPeriod(menuId, fromDt, toDt, samplePage)
                .stream()
                .map(r -> new AnalyticsDtos.FeedbackCommentSample(
                        null,
                        null,
                        r.getScore(),
                        truncate(r.getComment()),
                        r.getCreatedAt()
                ))
                .toList();

        List<AnalyticsDtos.FeedbackCommentSample> productComments = menuProductRatingRepository
                .sampleCommentsByMenuIdAndPeriod(menuId, fromDt, toDt, samplePage)
                .stream()
                .map(r -> new AnalyticsDtos.FeedbackCommentSample(
                        r.getMenuProductId(),
                        productNames.getOrDefault(r.getMenuProductId(), "Ürün #" + r.getMenuProductId()),
                        r.getScore(),
                        truncate(r.getComment()),
                        r.getCreatedAt()
                ))
                .toList();

        List<AnalyticsDtos.RatedProductSummary> topRated = menuProductRatingRepository
                .topRatedProductsByPeriod(menuId, fromDt, toDt, MIN_RATINGS_FOR_RANK, topPage)
                .stream()
                .map(row -> toRatedProduct(row, productNames))
                .toList();

        List<AnalyticsDtos.RatedProductSummary> bottomRated = menuProductRatingRepository
                .bottomRatedProductsByPeriod(menuId, fromDt, toDt, MIN_RATINGS_FOR_RANK, topPage)
                .stream()
                .map(row -> toRatedProduct(row, productNames))
                .toList();

        return new AnalyticsDtos.ReportFeedback(
                new AnalyticsDtos.MenuFeedbackSummary(
                        toAvg(menuRatingRepository.averageScoreByMenuIdAndPeriod(menuId, fromDt, toDt)),
                        menuRatingRepository.countByMenuIdAndPeriod(menuId, fromDt, toDt),
                        toAnalyticsHistogram(
                                menuRatingRepository.scoreHistogramByMenuIdAndPeriod(menuId, fromDt, toDt)),
                        menuComments
                ),
                new AnalyticsDtos.ProductFeedbackSummary(
                        toAvg(menuProductRatingRepository.averageScoreByMenuIdAndPeriod(menuId, fromDt, toDt)),
                        menuProductRatingRepository.countByMenuIdAndPeriod(menuId, fromDt, toDt),
                        topRated,
                        bottomRated,
                        toAnalyticsHistogram(
                                menuProductRatingRepository.scoreHistogramByMenuIdAndPeriod(menuId, fromDt, toDt)),
                        productComments
                )
        );
    }

    private Menu requireOwnedMenu(Long menuId) {
        Long ownerId = securityUtils.getCurrentUserId();
        Menu menu = menuRepository.findById(menuId)
                .filter(m -> !m.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menü bulunamadı"));
        if (!menu.getUserId().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu menüye erişim yetkiniz yok");
        }
        return menu;
    }

    private Map<Long, String> productNames(Long menuId) {
        return menuProductRepository.findByMenuIdAndDeletedFalseOrderBySortOrderAscProductIdAsc(menuId).stream()
                .collect(Collectors.toMap(MenuProduct::getProductId, MenuProduct::getName, (a, b) -> a));
    }

    private MenuDtos.FeedbackItemResponse toMenuItem(MenuRating rating) {
        return MenuDtos.FeedbackItemResponse.builder()
                .id(rating.getId())
                .type("menu")
                .score(rating.getScore())
                .comment(rating.getComment())
                .deviceType(rating.getDeviceType())
                .createdAt(rating.getCreatedAt())
                .build();
    }

    private MenuDtos.FeedbackItemResponse toProductItem(MenuProductRating rating, Map<Long, String> productNames) {
        return MenuDtos.FeedbackItemResponse.builder()
                .id(rating.getId())
                .type("product")
                .productId(rating.getMenuProductId())
                .productName(productNames.getOrDefault(
                        rating.getMenuProductId(),
                        "Ürün #" + rating.getMenuProductId()
                ))
                .score(rating.getScore())
                .comment(rating.getComment())
                .deviceType(rating.getDeviceType())
                .createdAt(rating.getCreatedAt())
                .build();
    }

    private MenuDtos.FeedbackPageResponse pageResponse(
            List<MenuDtos.FeedbackItemResponse> content,
            int page,
            int size,
            long total
    ) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) total / size);
        return MenuDtos.FeedbackPageResponse.builder()
                .content(content)
                .page(page)
                .size(size)
                .totalElements(total)
                .totalPages(totalPages)
                .hasNext(page + 1 < totalPages)
                .build();
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return "all";
        }
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "menu", "product", "all" -> normalized;
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "type menu, product veya all olmalıdır"
            );
        };
    }

    private BigDecimal toAvg(Double value) {
        double avg = value == null ? 0.0 : value;
        return BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP);
    }

    private List<MenuDtos.ScoreHistogramBucket> toHistogram(List<Object[]> rows) {
        return rows.stream()
                .map(row -> MenuDtos.ScoreHistogramBucket.builder()
                        .score(((Number) row[0]).intValue())
                        .count(((Number) row[1]).longValue())
                        .build())
                .toList();
    }

    private List<AnalyticsDtos.ScoreHistogramBucket> toAnalyticsHistogram(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new AnalyticsDtos.ScoreHistogramBucket(
                        ((Number) row[0]).intValue(),
                        ((Number) row[1]).longValue()
                ))
                .toList();
    }

    private AnalyticsDtos.RatedProductSummary toRatedProduct(Object[] row, Map<Long, String> productNames) {
        Long productId = ((Number) row[0]).longValue();
        BigDecimal avg = toAvg(row[1] instanceof Number n ? n.doubleValue() : 0.0);
        long count = ((Number) row[2]).longValue();
        return new AnalyticsDtos.RatedProductSummary(
                productId,
                productNames.getOrDefault(productId, "Ürün #" + productId),
                avg,
                count
        );
    }

    private String truncate(String comment) {
        if (comment == null) {
            return null;
        }
        String trimmed = comment.trim();
        if (trimmed.length() <= COMMENT_MAX_LEN) {
            return trimmed;
        }
        return trimmed.substring(0, COMMENT_MAX_LEN);
    }
}
