package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.MenuRating;
import com.ael.algoryqrservice.model.dto.MenuDtos;
import com.ael.algoryqrservice.repository.MenuRatingRepository;
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
public class MenuRatingService {

    private final MenuRepository menuRepository;
    private final MenuRatingRepository menuRatingRepository;

    @Transactional(readOnly = true)
    public MenuDtos.MenuRatingResponse getRating(Long menuId, HttpServletRequest httpRequest) {
        Menu menu = requirePublicMenu(menuId);
        String ip = extractIpAddress(httpRequest);
        Integer userRating = menuRatingRepository.findByMenuIdAndIpAddress(menuId, ip)
                .map(r -> (int) r.getScore())
                .orElse(null);
        return MenuDtos.MenuRatingResponse.builder()
                .menuId(menu.getMenuId())
                .ratingAvg(menu.getRatingAvg() == null ? BigDecimal.ZERO : menu.getRatingAvg())
                .ratingCount(menu.getRatingCount())
                .userRating(userRating)
                .build();
    }

    @Transactional
    public MenuDtos.MenuRatingResponse rateMenu(
            Long menuId,
            MenuDtos.MenuRatingRequest request,
            HttpServletRequest httpRequest
    ) {
        return rateMenu(menuId, request, extractIpAddress(httpRequest), extractUserAgent(httpRequest));
    }

    @Transactional
    public MenuDtos.MenuRatingResponse rateMenu(
            Long menuId,
            MenuDtos.MenuRatingRequest request,
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

        Menu menu = requirePublicMenu(menuId);
        String ip = (ipAddress == null || ipAddress.isBlank()) ? "0.0.0.0" : ipAddress.trim();
        LocalDateTime now = LocalDateTime.now();
        String comment = trimToNull(request.getComment());

        MenuRating rating = menuRatingRepository
                .findByMenuIdAndIpAddress(menuId, ip)
                .orElseGet(() -> MenuRating.builder()
                        .menuId(menuId)
                        .ipAddress(ip)
                        .createdAt(now)
                        .build());

        rating.setScore((short) score);
        rating.setComment(comment);
        rating.setUserAgent(userAgent);
        rating.setDeviceType(DeviceUtils.resolveDeviceType(userAgent));
        rating.setUpdatedAt(now);
        menuRatingRepository.save(rating);

        refreshMenuAggregates(menu);

        return MenuDtos.MenuRatingResponse.builder()
                .menuId(menu.getMenuId())
                .score(score)
                .comment(comment)
                .ratingAvg(menu.getRatingAvg())
                .ratingCount(menu.getRatingCount())
                .userRating(score)
                .build();
    }

    private Menu requirePublicMenu(Long menuId) {
        Menu menu = menuRepository.findById(menuId)
                .filter(m -> !m.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menü bulunamadı"));
        if (!menu.isActive()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Menü yayında değil");
        }
        if (!menu.isPublicAccessEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Lütfen restoran sahibiyle iletişime geçiniz.");
        }
        return menu;
    }

    private void refreshMenuAggregates(Menu menu) {
        Object avgRaw = menuRatingRepository.averageScoreByMenuId(menu.getMenuId());
        long count = menuRatingRepository.countByMenuId(menu.getMenuId());
        double avg = avgRaw instanceof Number number ? number.doubleValue() : 0.0;
        menu.setRatingAvg(BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP));
        menu.setRatingCount(count);
        menuRepository.save(menu);
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
