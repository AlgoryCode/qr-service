package com.ael.algoryqrservice.qrservice.menu.service;

import com.ael.algoryqrservice.qrservice.model.Branch;
import com.ael.algoryqrservice.qrservice.model.Menu;
import com.ael.algoryqrservice.qrservice.model.MenuReservation;
import com.ael.algoryqrservice.qrservice.menu.dto.MenuDtos;
import com.ael.algoryqrservice.qrservice.model.enums.MenuReservationStatus;
import com.ael.algoryqrservice.qrservice.repository.BranchRepository;
import com.ael.algoryqrservice.qrservice.repository.MenuRepository;
import com.ael.algoryqrservice.qrservice.repository.MenuReservationRepository;
import com.ael.algoryqrservice.qrservice.repository.MenuReservationSpecifications;
import com.ael.algoryqrservice.qrservice.util.DeviceUtils;
import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import com.ael.algoryqrservice.qrservice.model.User;

@Service
@RequiredArgsConstructor
public class MenuReservationService {

    private static final int RATE_LIMIT_MAX = 3;
    private static final int RATE_LIMIT_WINDOW_MINUTES = 5;
    /** Marks reservations the business entered in the panel, so the list can tell them from online ones. */
    public static final String PANEL_DEVICE_TYPE = "PANEL";

    private final MenuRepository menuRepository;
    private final BranchRepository branchRepository;
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
        // Reservations are kept per venue: a menu that is not placed in a branch cannot take them.
        if (menu.getBranchId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Bu menü için şu an rezervasyon alınmıyor. Lütfen restoranla iletişime geçin."
            );
        }
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
                .branchId(menu.getBranchId())
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

    /**
     * A reservation the business takes itself (by phone or at the door). Contact details are optional and the
     * time may be in the past (to log a walk-in); it starts confirmed because the business made it.
     */
    @Transactional
    public MenuDtos.ReservationResponse createForOwner(
            Long menuId,
            MenuDtos.ReservationCreateRequest request,
            String ipAddress
    ) {
        Menu menu = requireOwnedMenu(menuId);
        return createByBusiness(menu.getMenuId(), menu.getBranchId(), request, ipAddress);
    }

    /** A reservation the business takes for a branch; it names no menu. */
    @Transactional
    public MenuDtos.ReservationResponse createForBranch(
            Long branchId,
            MenuDtos.ReservationCreateRequest request,
            String ipAddress
    ) {
        Branch branch = requireOwnedBranch(branchId);
        return createByBusiness(null, branch.getId(), request, ipAddress);
    }

    private MenuDtos.ReservationResponse createByBusiness(
            Long menuId,
            Long branchId,
            MenuDtos.ReservationCreateRequest request,
            String ipAddress
    ) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "İstek gövdesi zorunludur");
        }
        String customerName = requireText(request.getCustomerName(), "Ad soyad zorunludur", 120);
        requirePartySize(request.getPartySize());
        if (request.getReservationAt() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rezervasyon tarihi/saati zorunludur");
        }

        LocalDateTime now = LocalDateTime.now();
        MenuReservation reservation = MenuReservation.builder()
                .menuId(menuId)
                .branchId(branchId)
                .customerName(customerName)
                .phone(trimToNull(request.getPhone()))
                .email(normalizeEmail(request.getEmail()))
                .partySize(request.getPartySize())
                .reservationAt(request.getReservationAt())
                .status(MenuReservationStatus.ACTIVE)
                .note(trimToNull(request.getNote()))
                .ipAddress(ipAddress == null || ipAddress.isBlank() ? "0.0.0.0" : ipAddress.trim())
                .deviceType(PANEL_DEVICE_TYPE)
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
        ListFilter filter = listFilter(status, from, to, q);
        return listPage(
                MenuReservationSpecifications.forOwner(menuId, filter.status(), filter.from(), filter.to(), filter.pattern()),
                page,
                size
        );
    }

    /** Every reservation of the branch: booked from any of its menus or entered in the panel. */
    @Transactional(readOnly = true)
    public MenuDtos.ReservationPageResponse listForBranch(
            Long branchId,
            String status,
            LocalDate from,
            LocalDate to,
            String q,
            int page,
            int size
    ) {
        requireOwnedBranch(branchId);
        ListFilter filter = listFilter(status, from, to, q);
        return listPage(
                MenuReservationSpecifications.forBranch(branchId, filter.status(), filter.from(), filter.to(), filter.pattern()),
                page,
                size
        );
    }

    private record ListFilter(MenuReservationStatus status, LocalDateTime from, LocalDateTime to, String pattern) {
    }

    private ListFilter listFilter(String status, LocalDate from, LocalDate to, String q) {
        String query = trimToNull(q);
        return new ListFilter(
                parseStatusFilter(status),
                from == null ? null : from.atStartOfDay(),
                to == null ? null : to.plusDays(1).atStartOfDay().minusNanos(1),
                query == null ? null : "%" + query.toLowerCase(Locale.ROOT) + "%"
        );
    }

    private MenuDtos.ReservationPageResponse listPage(Specification<MenuReservation> spec, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : Math.min(size, 100);
        Pageable pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Order.desc("reservationAt"), Sort.Order.desc("createdAt"))
        );
        Page<MenuReservation> result = menuReservationRepository.findAll(spec, pageable);

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
        requireSomeEdit(request);
        MenuReservation reservation = menuReservationRepository.findById(reservationId)
                .filter(r -> menuId.equals(r.getMenuId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rezervasyon bulunamadı"));
        return applyUpdate(reservation, request);
    }

    @Transactional
    public MenuDtos.ReservationResponse updateForBranch(
            Long branchId,
            Long reservationId,
            MenuDtos.ReservationUpdateRequest request
    ) {
        requireOwnedBranch(branchId);
        requireSomeEdit(request);
        MenuReservation reservation = menuReservationRepository.findById(reservationId)
                .filter(r -> branchId.equals(r.getBranchId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rezervasyon bulunamadı"));
        return applyUpdate(reservation, request);
    }

    private static void requireSomeEdit(MenuDtos.ReservationUpdateRequest request) {
        if (request == null || (request.getStatus() == null && request.getReservationAt() == null && !hasDetailEdits(request))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Güncellenecek alan belirtilmedi");
        }
    }

    private MenuDtos.ReservationResponse applyUpdate(MenuReservation reservation, MenuDtos.ReservationUpdateRequest request) {

        if (request.getStatus() != null) {
            validateStatusTransition(reservation.getStatus(), request.getStatus());
            reservation.setStatus(request.getStatus());
        }
        if (request.getReservationAt() != null) {
            reservation.setReservationAt(request.getReservationAt());
        }
        if (hasDetailEdits(request)) {
            if (reservation.getStatus() == MenuReservationStatus.CANCELED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "İptal edilmiş rezervasyon güncellenemez");
            }
            applyDetailEdits(reservation, request);
        }
        reservation.setUpdatedAt(LocalDateTime.now());
        return toResponse(menuReservationRepository.save(reservation));
    }

    private static boolean hasDetailEdits(MenuDtos.ReservationUpdateRequest request) {
        return request.getCustomerName() != null || request.getPhone() != null || request.getEmail() != null
                || request.getPartySize() != null || request.getNote() != null;
    }

    /** An empty phone, e-mail or note clears it; the name cannot be cleared. */
    private void applyDetailEdits(MenuReservation reservation, MenuDtos.ReservationUpdateRequest request) {
        if (request.getCustomerName() != null) {
            reservation.setCustomerName(requireText(request.getCustomerName(), "Ad soyad zorunludur", 120));
        }
        if (request.getPhone() != null) {
            reservation.setPhone(trimToNull(request.getPhone()));
        }
        if (request.getEmail() != null) {
            reservation.setEmail(normalizeEmail(request.getEmail()));
        }
        if (request.getPartySize() != null) {
            requirePartySize(request.getPartySize());
            reservation.setPartySize(request.getPartySize());
        }
        if (request.getNote() != null) {
            reservation.setNote(trimToNull(request.getNote()));
        }
    }

    private static void requirePartySize(Integer partySize) {
        if (partySize == null || partySize < 1 || partySize > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kişi sayısı 1 ile 50 arasında olmalıdır");
        }
    }

    private String normalizeEmail(String value) {
        String email = trimToNull(value);
        if (email == null) {
            return null;
        }
        if (!email.contains("@")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geçerli bir e-posta giriniz");
        }
        return email.toLowerCase(Locale.ROOT);
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

    private Branch requireOwnedBranch(Long branchId) {
        Long ownerId = securityUtils.getCurrentMerchantId();
        return branchRepository.findByIdAndMerchantIdAndDeletedFalse(branchId, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Şube bulunamadı"));
    }

    private Menu requireOwnedMenu(Long menuId) {
        Long ownerId = securityUtils.getCurrentMerchantId();
        Menu menu = menuRepository.findById(menuId)
                .filter(m -> !m.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menü bulunamadı"));
        if (!menu.getMerchantId().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu menüye erişim yetkiniz yok");
        }
        return menu;
    }

    private MenuDtos.ReservationResponse toResponse(MenuReservation reservation) {
        return MenuDtos.ReservationResponse.builder()
                .id(reservation.getId())
                .menuId(reservation.getMenuId())
                .branchId(reservation.getBranchId())
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

    public String extractIpAddress(HttpServletRequest request) {
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
