package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.MenuReservation;
import com.ael.algoryqrservice.model.dto.MenuDtos;
import com.ael.algoryqrservice.model.enums.MenuReservationStatus;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.MenuReservationRepository;
import com.ael.algoryqrservice.repository.MenuReservationSpecifications;
import com.ael.algoryqrservice.util.DeviceUtils;
import com.ael.algoryqrservice.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class MenuReservationService {

    private static final int RATE_LIMIT_MAX = 3;
    private static final int RATE_LIMIT_WINDOW_MINUTES = 5;

    private final MenuRepository menuRepository;
    private final MenuReservationRepository menuReservationRepository;
    private final SecurityUtils securityUtils;

    @Transactional
    public MenuDtos.ReservationResponse createPublic(
            Long menuId,
            MenuDtos.ReservationCreateRequest request,
            HttpServletRequest httpRequest
    ) {
        return createPublic(
                menuId,
                request,
                extractIpAddress(httpRequest),
                extractUserAgent(httpRequest)
        );
    }

    @Transactional
    public MenuDtos.ReservationResponse createPublic(
            Long menuId,
            MenuDtos.ReservationCreateRequest request,
            String ipAddress,
            String userAgent
    ) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "İstek gövdesi zorunludur");
        }

        Menu menu = requirePublicMenu(menuId);
        String customerName = requireText(request.getCustomerName(), "Ad soyad zorunludur", 120);
        String phone = trimToNull(request.getPhone());
        String email = trimToNull(request.getEmail());
        if (phone == null && email == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Telefon veya e-posta zorunludur");
        }
        if (email != null) {
            email = email.toLowerCase(Locale.ROOT);
            if (!email.contains("@")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geçerli bir e-posta giriniz");
            }
        }
        if (request.getPartySize() == null || request.getPartySize() < 1 || request.getPartySize() > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kişi sayısı 1 ile 50 arasında olmalıdır");
        }
        if (request.getReservationAt() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rezervasyon tarihi/saati zorunludur");
        }
        if (request.getReservationAt().isBefore(LocalDateTime.now().minusMinutes(1))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rezervasyon geçmiş bir zamana yapılamaz");
        }

        String ip = (ipAddress == null || ipAddress.isBlank()) ? "0.0.0.0" : ipAddress.trim();
        LocalDateTime windowStart = LocalDateTime.now().minusMinutes(RATE_LIMIT_WINDOW_MINUTES);
        long recent = menuReservationRepository.countByMenuIdAndIpAddressAndCreatedAtAfter(menuId, ip, windowStart);
        if (recent >= RATE_LIMIT_MAX) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Çok fazla rezervasyon isteği. Lütfen birkaç dakika sonra tekrar deneyin."
            );
        }

        LocalDateTime now = LocalDateTime.now();
        MenuReservation reservation = MenuReservation.builder()
                .menuId(menu.getMenuId())
                .customerName(customerName)
                .phone(phone)
                .email(email)
                .partySize(request.getPartySize())
                .reservationAt(request.getReservationAt())
                .status(MenuReservationStatus.PENDING)
                .note(trimToNull(request.getNote()))
                .ipAddress(ip)
                .userAgent(userAgent)
                .deviceType(DeviceUtils.resolveDeviceType(userAgent))
                .createdAt(now)
                .updatedAt(now)
                .build();

        return toResponse(menuReservationRepository.save(reservation));
    }

    @Transactional(readOnly = true)
    public MenuDtos.ReservationPageResponse listForOwner(
            Long menuId,
            String status,
            LocalDate from,
            LocalDate to,
            String q,
            int page,
            int size
    ) {
        requireOwnedMenu(menuId);
        MenuReservationStatus statusFilter = parseStatusFilter(status);
        LocalDateTime fromDt = from == null ? null : from.atStartOfDay();
        LocalDateTime toDt = to == null ? null : to.plusDays(1).atStartOfDay().minusNanos(1);
        String query = trimToNull(q);
        String pattern = query == null ? null : "%" + query.toLowerCase(Locale.ROOT) + "%";

        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : Math.min(size, 100);
        Pageable pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Order.desc("reservationAt"), Sort.Order.desc("createdAt"))
        );
        Page<MenuReservation> result = menuReservationRepository.findAll(
                MenuReservationSpecifications.forOwner(menuId, statusFilter, fromDt, toDt, pattern),
                pageable
        );

        return MenuDtos.ReservationPageResponse.builder()
                .content(result.getContent().stream().map(this::toResponse).toList())
                .page(safePage)
                .size(safeSize)
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .hasNext(result.hasNext())
                .build();
    }

    @Transactional
    public MenuDtos.ReservationResponse updateForOwner(
            Long menuId,
            Long reservationId,
            MenuDtos.ReservationUpdateRequest request
    ) {
        requireOwnedMenu(menuId);
        if (request == null || (request.getStatus() == null && request.getReservationAt() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Güncellenecek alan belirtilmedi");
        }

        MenuReservation reservation = menuReservationRepository.findById(reservationId)
                .filter(r -> r.getMenuId().equals(menuId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rezervasyon bulunamadı"));

        if (request.getStatus() != null) {
            validateStatusTransition(reservation.getStatus(), request.getStatus());
            reservation.setStatus(request.getStatus());
        }
        if (request.getReservationAt() != null) {
            reservation.setReservationAt(request.getReservationAt());
        }
        reservation.setUpdatedAt(LocalDateTime.now());
        return toResponse(menuReservationRepository.save(reservation));
    }

    private void validateStatusTransition(MenuReservationStatus current, MenuReservationStatus next) {
        if (current == next) {
            return;
        }
        if (current == MenuReservationStatus.CANCELED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "İptal edilmiş rezervasyon güncellenemez");
        }
        if (next == MenuReservationStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rezervasyon tekrar beklemeye alınamaz");
        }
        if (current == MenuReservationStatus.PENDING
                && (next == MenuReservationStatus.ACTIVE || next == MenuReservationStatus.CANCELED)) {
            return;
        }
        if (current == MenuReservationStatus.ACTIVE && next == MenuReservationStatus.CANCELED) {
            return;
        }
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Geçersiz durum geçişi: " + current + " -> " + next
        );
    }

    private MenuReservationStatus parseStatusFilter(String status) {
        if (status == null || status.isBlank() || "all".equalsIgnoreCase(status.trim())) {
            return null;
        }
        try {
            return MenuReservationStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "status PENDING, ACTIVE, CANCELED veya all olmalıdır"
            );
        }
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

    private MenuDtos.ReservationResponse toResponse(MenuReservation reservation) {
        return MenuDtos.ReservationResponse.builder()
                .id(reservation.getId())
                .menuId(reservation.getMenuId())
                .customerName(reservation.getCustomerName())
                .phone(reservation.getPhone())
                .email(reservation.getEmail())
                .partySize(reservation.getPartySize())
                .reservationAt(reservation.getReservationAt())
                .status(reservation.getStatus())
                .note(reservation.getNote())
                .deviceType(reservation.getDeviceType())
                .createdAt(reservation.getCreatedAt())
                .updatedAt(reservation.getUpdatedAt())
                .build();
    }

    private String requireText(String value, String message, int maxLen) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        if (trimmed.length() > maxLen) {
            return trimmed.substring(0, maxLen);
        }
        return trimmed;
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
