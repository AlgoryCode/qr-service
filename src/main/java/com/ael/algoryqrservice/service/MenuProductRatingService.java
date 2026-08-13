package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.MenuProduct;
import com.ael.algoryqrservice.model.MenuProductRating;
import com.ael.algoryqrservice.model.dto.MenuDtos;
import com.ael.algoryqrservice.repository.MenuProductRatingRepository;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.util.DeviceUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MenuProductRatingService {

    private final MenuRepository menuRepository;
    private final MenuProductRepository menuProductRepository;
    private final MenuProductRatingRepository menuProductRatingRepository;

    @Transactional
    public MenuDtos.ProductRatingResponse rateProduct(
            Long menuId,
            Long productId,
            MenuDtos.ProductRatingRequest request,
            HttpServletRequest httpRequest
    ) {
        return rateProduct(
                menuId,
                productId,
                request,
                extractIpAddress(httpRequest),
                extractUserAgent(httpRequest)
        );
    }

    @Transactional
    public MenuDtos.ProductRatingResponse rateProduct(
            Long menuId,
            Long productId,
            MenuDtos.ProductRatingRequest request,
            String ipAddress,
            String userAgent
    ) {
        if (request == null || request.getScore() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Puan zorunludur");
        }
        int score = request.getScore();
        if (score < 1 || score > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Puan 1 ile 5 arasında olmalıdır");
        }

        Menu menu = menuRepository.findById(menuId)
                .filter(m -> !m.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menü bulunamadı"));
        if (!menu.isActive()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Menü yayında değil");
        }
        if (!menu.isPublicAccessEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Lütfen restoran sahibiyle iletişime geçiniz.");
        }

        MenuProduct product = menuProductRepository.findByProductIdAndDeletedFalse(productId)
                .filter(p -> p.getMenuId().equals(menuId))
                .filter(MenuProduct::isAvailable)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ürün bulunamadı"));

        String ip = (ipAddress == null || ipAddress.isBlank()) ? "0.0.0.0" : ipAddress.trim();
        LocalDateTime now = LocalDateTime.now();
        String comment = trimToNull(request.getComment());

        MenuProductRating rating = menuProductRatingRepository
                .findByMenuProductIdAndIpAddress(productId, ip)
                .orElseGet(() -> MenuProductRating.builder()
                        .menuId(menuId)
                        .menuProductId(productId)
                        .ipAddress(ip)
                        .createdAt(now)
                        .build());

        rating.setScore((short) score);
        rating.setComment(comment);
        rating.setUserAgent(userAgent);
        rating.setDeviceType(DeviceUtils.resolveDeviceType(userAgent));
        rating.setUpdatedAt(now);
        menuProductRatingRepository.save(rating);

        refreshProductAggregates(product);

        return MenuDtos.ProductRatingResponse.builder()
                .productId(product.getProductId())
                .menuId(product.getMenuId())
                .score(score)
                .comment(comment)
                .ratingAvg(product.getRatingAvg())
                .ratingCount(product.getRatingCount())
                .userRating(score)
                .build();
    }

    private void refreshProductAggregates(MenuProduct product) {
        Object avgRaw = menuProductRatingRepository.averageScoreByProductId(product.getProductId());
        long count = menuProductRatingRepository.countByMenuProductId(product.getProductId());
        double avg = avgRaw instanceof Number number ? number.doubleValue() : 0.0;
        product.setRatingAvg(BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP));
        product.setRatingCount(count);
        menuProductRepository.save(product);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String extractIpAddress(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String extractUserAgent(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        return ua == null || ua.isBlank() ? null : ua.trim();
    }
}
